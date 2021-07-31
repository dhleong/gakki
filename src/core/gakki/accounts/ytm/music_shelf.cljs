(ns gakki.accounts.ytm.music-shelf
  (:require [applied-science.js-interop :as j]
            [gakki.accounts.ytm.util :as util :refer [->seconds
                                                      runs->text
                                                      unpack-navigation-endpoint]]
            [gakki.util.logging :as log]))

(def ^:private ignored-section-titles #{"Videos"})

(defn- parse-flex-column-item [^js item]
  (if-let [root (j/get item :musicResponsiveListItemFlexColumnRenderer)]
    (let [from-endpoint (unpack-navigation-endpoint (j/get root :text))]
      (assoc from-endpoint
             :title (runs->text (j/get root :text))))

    (throw (ex-info "Unexpected flexColumn item"
                    {:item item}))))

(defn- resolve-generic-shelf-item [item items]
  (cond
    (#{:album :artist :playlist} (:kind item))
    (-> item
        (dissoc :duration :items)
        (assoc :title (:title (first items))))

    (= :track (:kind (first items)))
    (let [[artist album duration] (-> items
                                      second
                                      :title
                                      util/split-string-by-dots)]
      (assoc (first items)
             :artist artist
             :album album
             :duration (->seconds duration)))

    :else
    (throw (ex-info "Unexpected generic shelf item" {:item item}))))

(defn- compose-shelf-item [{:keys [duration image-url items] :as item}]
  ; TODO radio id?
  (cond
    (and (> (count items) 2)
         (= :track (:kind (first items)))
         (or (= :artist (:kind (second items)))
             (= :album (:kind (nth items 2)))))
    (assoc (first items)
           :duration duration
           :image-url image-url
           :artist (:title (second items))
           :album (:title (nth items 2)))

    (= (count items) 2)
    (resolve-generic-shelf-item item items)

    ; NOTE: It is apparently possible to have items that don't have an ID!
    ; The Web UI renders them as disabled, and the only available action is to
    ; remove them from the playlist. For now, we will simply omit them.
    (nil? (:id item))
    nil

    :else
    (assoc item
           :provider :ytm
           :kind :unknown)))


; ======= Shelf item parsing ==============================

(defmulti parse-shelf-item (fn [^js item] (first (js/Object.keys item))))

(defmethod parse-shelf-item "musicResponsiveListItemRenderer"
  [^js item]
  (if-let [flex-columns (j/get-in item [:musicResponsiveListItemRenderer
                                        :flexColumns])]
    (let [renderer (j/get item :musicResponsiveListItemRenderer)
          duration-runs (j/get-in renderer [:fixedColumns
                                            0
                                            :musicResponsiveListItemFixedColumnRenderer
                                            :text])
          endpoint (unpack-navigation-endpoint renderer)]
      (compose-shelf-item
        (merge
          endpoint
          {:image-url (-> item
                          (j/get-in [:musicResponsiveListItemRenderer :thumbnail])
                          util/pick-thumbnail)
           :duration (some-> duration-runs
                             runs->text
                             ->seconds)
           :items (keep parse-flex-column-item flex-columns)})))

    (throw (ex-info "Unexpected musicResponsiveListItemRenderer contents"
                    {:contents item}))))

(defmethod parse-shelf-item "musicTwoRowItemRenderer"
  [^js item]
  (let [root (j/get item :musicTwoRowItemRenderer)
        title (j/get root :title)
        endpoint (unpack-navigation-endpoint title)]
    (assoc endpoint
           :title (runs->text title)
           :subtitle (when-let [subtitle (j/get root :subtitle)]
                       (runs->text subtitle))
           :image-url (-> root
                          (j/get :thumbnailRenderer)
                          util/pick-thumbnail))))


; ======= Shelf parsing ===================================

(defmulti music-shelf->section (fn [container] (first (js/Object.keys container))))

(defmethod music-shelf->section "musicShelfRenderer"
  [^js container]
  (j/let [^:js {renderer :musicShelfRenderer} container
          ^:js {:keys [title contents]} renderer
          title (runs->text title)]
    (when-not (contains? ignored-section-titles title)
      {:title title
       :items (vec (keep parse-shelf-item contents))})))

(defmethod music-shelf->section "musicPlaylistShelfRenderer"
  [^js container]
  (j/let [^:js {{:keys [contents]} :musicPlaylistShelfRenderer} container]
    {:items (vec (keep parse-shelf-item contents))}))

(defmethod music-shelf->section "musicCarouselShelfRenderer"
  [^js container]
  (j/let [^:js {renderer :musicCarouselShelfRenderer} container
          ^:js {:keys [header contents]} renderer
          title (runs->text (j/get-in header [:musicCarouselShelfBasicHeaderRenderer
                                              :title]))]
    (when-not (contains? ignored-section-titles title)
      {:title title
       :items (vec (keep parse-shelf-item contents))})))

(defmethod music-shelf->section "musicDescriptionShelfRenderer" [_]
  ; Probably can skip quietly
  nil)

(defmethod music-shelf->section :default
  [^js section]
  (log/error "Unexpected music shelf section: " section)
  nil)
