(ns gakki.util.media)

(defn category-id [category]
  (or (:id category)
      (:title category)))

