(ns gakki.accounts.ytm.music-shelf
  (:require [applied-science.js-interop :as j]
            [gakki.accounts.ytm.util :as util :refer [->seconds
                                                      runs->text
                                                      unpack-navigation-endpoint]]
            [gakki.util.logging :as log]))

(def ^:private ignored-section-titles #{"Videos"})

(defn- parse-flex-column-item [^js item]
  (if-some [title (j/get item .-title)]
    {:title (str title)}

    (if-let [root (j/get item :musicResponsiveListItemFlexColumnRenderer)]
      (let [from-endpoint (unpack-navigation-endpoint (j/get root :text))]
        (assoc from-endpoint
               :title (runs->text (j/get root :text))))

      (throw (ex-info "Unexpected flexColumn item"
                      {:item item})))))

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

(defn- container->type [^js item]
  (or (some-> (j/get item .-type)
              keyword)
      (first (js/Object.keys item))))

(defmulti parse-shelf-item container->type)

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
        endpoint (or (unpack-navigation-endpoint title)
                     (unpack-navigation-endpoint root))]
    (assoc endpoint
           :title (runs->text title)
           :subtitle (when-let [subtitle (j/get root :subtitle)]
                       (runs->text subtitle))
           :image-url (-> root
                          (j/get :thumbnailRenderer)
                          util/pick-thumbnail))))

(defmethod parse-shelf-item :MusicTwoRowItem
  [^js item]
  #_{:clj-kondo/ignore [:inline-def :unused-private-var]}
  (def ^:private last-item item)
  (let [title (str (j/get item .-title))
        subtitle (some-> item
                         (j/get .-subtitle)
                         (str))
        endpoint (unpack-navigation-endpoint item)]
    (assoc endpoint
           :title title
           :subtitle subtitle
           :image-url (-> item
                          (j/get .-thumbnail)
                          util/pick-thumbnail))))

(defmethod parse-shelf-item :MusicResponsiveListItem
  [^js item]
  (if-let [flex (j/get item .-flex_columns)]
    (let [item-endpoint (unpack-navigation-endpoint item)
          album-name (some-> (j/get-in item [.-album .-name])
                             str)
          artist-name (some-> (or (j/get item .-artist)
                                  (first (j/get item .-artists)))
                              (j/get .-name)
                              str)]
      (when-some [endpoint (or item-endpoint
                               (unpack-navigation-endpoint (j/get item .-album))
                               (unpack-navigation-endpoint (j/get item .-artist)))]
        (merge
         endpoint
         {:title (or (when item-endpoint
                       (str (j/get item .-title)))
                     album-name
                     artist-name)
          :album album-name
          :artist artist-name
          :image-url (-> item
                         (j/get .-thumbnail)
                         util/pick-thumbnail)
          :duration (some-> item
                            (j/get-in [.-duration .-seconds]))
          :items (keep parse-flex-column-item flex)})))

    ; TODO:  support fixed_columns?
    (throw (ex-info "Unexpected musicResponsiveListItemRenderer contents"
                    {:contents item}))))

(defmethod parse-shelf-item :ContinuationItem
  [_]
  (log/debug "TODO: Continuation item")
  nil)

; ======= Shelf parsing ===================================

(defmulti music-shelf->section
  container->type)

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

;; NEW: youtubei.js/innertube types:

(defmethod music-shelf->section :MusicCarouselShelf
  [^js carousel]
  (j/let [^:js {:keys [header contents]} carousel
          title (str (j/get header .-title))]
    (when-not (contains? ignored-section-titles title)
      {:title title
       :items (vec (keep parse-shelf-item contents))})))

(defmethod music-shelf->section :MusicTastebuilderSelf
  [^js _carousel]
  ; TODO: support?
  nil)

;; Fallback:

(defmethod music-shelf->section :default
  [^js section]
  (log/error "Unexpected music shelf section: " section
             (str  "(type=" (container->type section) ")"))
  nil)
