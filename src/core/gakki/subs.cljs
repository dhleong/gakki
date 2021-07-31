(ns gakki.subs
  (:require [re-frame.core :refer [reg-sub]]
            [gakki.db :as db]
            [gakki.const :as const :refer [max-volume-int]]
            [gakki.util.media :refer [category-id]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)
(reg-sub :artists :artist)
(reg-sub :playlists :playlist)
(reg-sub :radios :radio)
(reg-sub ::carousel-data ::carousel-data)
(reg-sub ::prefs :prefs)

(reg-sub
  :loading?
  (fn [db _]
    (> (:loading-count db) 0)))

(reg-sub
  :initializing?
  :<- [:loading?]
  :<- [:page]
  :<- [:carousel/categories]
  :<- [:queue/items]
  (fn [[loading? page categories queue] _]
    (and loading?
         (= [:home] page)
         (empty? categories)
         (empty? queue))))


; ======= auth ============================================

(reg-sub
  :account
  :<- [:accounts]
  (fn [accounts [_ provider-id]]
    (get accounts provider-id)))


; ======= prefs ===========================================

(reg-sub
  :prefs
  :<- [::prefs]
  (fn [prefs [_ ?pref]]
    (if ?pref
      (if-some [pref (get prefs ?pref)]
        pref
        (get db/default-prefs ?pref))
      prefs)))


; ======= player ==========================================

(reg-sub
  ::player-volume
  (fn [db _]
    (get-in db [:player :volume] max-volume-int)))

(reg-sub
  :player/volume-suppress-amount
  :<- [:var :voice-connected]
  (fn [voice-connected-integrations _]
    (if (empty? voice-connected-integrations)
      1.0
      const/suppressed-volume-percent)))

(reg-sub
  :player/volume-percent
  :<- [::player-volume]
  :<- [:player/volume-suppress-amount]
  (fn [[volume suppress-amount] _]
    (let [base-percent (/ volume max-volume-int)]
      (* base-percent suppress-amount))))

(reg-sub
  :player/adjusting-volume-percent
  (fn [db _]
    (when (> (get-in db [:player :adjusting-volume?]) 0)
      (/ (get-in db [:player :volume] max-volume-int)
         max-volume-int))))

(reg-sub
  :player/item
  (fn [db _]
    (get-in db [:player :current])))

(reg-sub
  :player/queue
  (fn [db _]
    (get-in db [:player :queue])))

(reg-sub
  :player/state
  (fn [db _]
    (get-in db [:player :state])))


; ======= queue ===========================================

(reg-sub
  :queue/items
  :<- [:player/queue]
  (fn [{:keys [items]} _]
    items))

(reg-sub
  :queue/duration-seconds
  :<- [:queue/items]
  (fn [queue]
    (->> queue
         (transduce
           (map :duration)
           +))))

(reg-sub
  :queue/duration-display
  :<- [:queue/duration-seconds]
  (fn [seconds]
    (cond
      (> seconds (* 2 3600))
      (str (/ (js/Math.floor (/ seconds 360))
              10)
           " hrs")

      (> seconds 120)
      (str (js/Math.floor (/ seconds 60))
           " mins")

      :else
      (str seconds " s")
      )))

(reg-sub
  :queue/items-with-state
  :<- [:player/queue]
  (fn [{queue :items current-index :index} _]
    (update queue current-index assoc :current? true)))


; ======= home ============================================

(reg-sub
  ::home-categories
  (fn [db _]
    ; TODO probably some sort of round-robin?
    (->> (:home/categories db)
         vals
         (apply concat))))

(reg-sub
  ::home-selection
  (fn [db _]
    (:home/selection db)))


; ======= Persistent page-based Carousels =================

(reg-sub
  ::carousel-categories
  :<- [:page]
  :<- [::home-categories]
  :<- [:artists]
  :<- [:search/results]
  (fn [[page home-categories artists search-results] _]
    (case (first page)
      :home home-categories
      :artist (get-in artists [(second page) :categories])
      :search/results search-results

      nil)))

(reg-sub
  ::carousel-selection
  :<- [:page]
  :<- [::carousel-data]
  (fn [[page carousel-data]]
    (:selection (get-in carousel-data page))))

(reg-sub
  :carousel/selection
  :<- [::carousel-categories]
  :<- [::carousel-selection]
  (fn [[categories selections] _]
    (or (when (and (:category selections)
                   (:item selections))
          selections)

        (when-let [category (or (when-let [id (:category selections)]
                                  (->> categories
                                       (filter #(= id (category-id %)))
                                       first))
                                (first categories))]
          {:category (category-id category)
           :item (let [item (first (:items category))]
                   (:id item))}))))

(reg-sub
  :carousel/categories
  :<- [::carousel-categories]
  :<- [:carousel/selection]
  (fn [[categories {selected-cat :category selected-item :item}] _]
    (->> categories
         (map (fn [category]
                (if-not (= (category-id category)
                           selected-cat)
                  category
                  (-> category
                      (assoc :selected? true)
                      (update :items
                              (partial
                                map
                                (fn [item]
                                  (if (= (:id item)
                                         selected-item)
                                    (assoc item :selected? true)
                                    item)))))))))))


; ======= entities ========================================

(reg-sub
  :album
  (fn [db [_ id]]
    (get-in db [:album id])))

(defn- reg-entity-sub-by-id
  ([sub-id _<- entity-map-sub-id]
   (reg-entity-sub-by-id {} sub-id _<- entity-map-sub-id))
  ([{:keys [stateful?]} sub-id _<- entity-map-sub-id]
   (reg-sub
     sub-id
     :<- entity-map-sub-id
     (fn [entities [_ id]]
       (get entities id)))

   (when stateful?
     (reg-sub
       (keyword (name sub-id) "items-with-state")
       :<- entity-map-sub-id
       :<- [:player/item]
       (fn [[entities now-playing] [_ id]]
         (let [items (get-in entities [id :items])]
           (->> items
                (mapv #(if (= (:id %) (:id now-playing))
                         (assoc % :current? true)
                         %)))))))))

(reg-entity-sub-by-id :artist :<- [:artists])
(reg-entity-sub-by-id {:stateful? true} :playlist :<- [:playlists])
(reg-entity-sub-by-id {:stateful? true} :radio :<- [:radios])


; ======= integrations ====================================

(reg-sub
  :integration-vars
  (fn [db _]
    (get db :integration-vars)))

(reg-sub
  :var
  :<- [:integration-vars]
  (fn [vars [_ var-name]]
    (get vars var-name)))


; ======= Search ==========================================

(reg-sub
  ::search
  (fn [db]
    (:search db)))

(reg-sub
  ::search-raw-page-state
  :<- [:page]
  :<- [::search]
  (fn [[[_ query] search] _]
    (get search query)))

(reg-sub
  :search/results
  :<- [::search-raw-page-state]
  (fn [state _]
    (if (vector? state)
      state
      [])))

(reg-sub
  :search/state
  :<- [::search-raw-page-state]
  (fn [state _]
    (cond
      (vector? state) :ready
      (keyword? state) state
      (nil? state) :empty
      :else :error)))

(reg-sub
  :search/error
  :<- [::search-raw-page-state]
  (fn [state _]
    (cond
      (vector? state) nil
      (keyword? state) nil
      :else state)))
