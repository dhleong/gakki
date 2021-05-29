(ns gakki.subs
  (:require [gakki.const :refer [max-volume-int]]
            [re-frame.core :refer [reg-sub]]
            [gakki.util.media :refer [category-id]]))

(reg-sub :page :page)
(reg-sub :accounts :accounts)

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
  :player/state
  (fn [db _]
    (get-in db [:player :state])))


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

(reg-sub
  :home/selection
  :<- [::home-categories]
  :<- [::home-selection]
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
  :home/categories
  :<- [::home-categories]
  :<- [:home/selection]
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
  (fn [db [_ id]]
    (get-in db [:artist id])))
