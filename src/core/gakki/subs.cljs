(ns gakki.subs
  (:require [gakki.const :refer [max-volume-int]]
            [re-frame.core :refer [reg-sub]]
            [gakki.util.media :refer [category-id]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)
(reg-sub :artists :artist)
(reg-sub :playlists :playlist)
(reg-sub ::carousel-data ::carousel-data)

(reg-sub
  :loading?
  (fn [db _]
    (> (:loading-count db) 0)))


; ======= auth ============================================

(reg-sub
  :account
  :<- [:accounts]
  (fn [accounts [_ provider-id]]
    (get accounts provider-id)))


; ======= player ==========================================

(reg-sub
  :player/volume-percent
  (fn [db _]
    (/ (get-in db [:player :volume] max-volume-int)
       max-volume-int)))

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
  (fn [[page home-categories artists] _]
    (case (first page)
      :home home-categories
      :artist (get-in artists [(second page) :categories])

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

(reg-sub
  :artist
  :<- [:artists]
  (fn [artists [_ id]]
    (get artists id)))

(reg-sub
  :playlist
  :<- [:playlists]
  (fn [playlists [_ id]]
    (get playlists id)))

(reg-sub
  :playlist/items-with-state
  :<- [:playlists]
  :<- [:player/item]
  (fn [[playlists now-playing] [_ id]]
    (let [items (get-in playlists [id :items])]
      (->> items
           (mapv #(if (= (:id %) (:id now-playing))
                    (assoc % :current? true)
                    %))))))
