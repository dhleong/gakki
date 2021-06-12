(ns gakki.accounts.ytm.album
  (:refer-clojure :exclude [load])
  (:require [applied-science.js-interop :as j]
            [promesa.core :as p]
            ["ytmusic/dist/lib/utils" :rename {sendRequest send-request}]
            ["ytmusic" :rename {YTMUSIC YTMusic}]
            [gakki.accounts.ytm.playlist :as playlist]))

(defmulti ^:private apply-mutation (fn [_state mutation]
                                     (j/get mutation :type)))

(defmethod apply-mutation "ENTITY_MUTATION_TYPE_REPLACE"
  [state mutation]
  (j/let [^:js {id :entityKey payload :payload} mutation
          entity-type (-> (js/Object.keys payload)
                          first
                          keyword)
          entity (-> (j/get payload entity-type)
                     (j/assoc! :type entity-type))]
    (-> state
        (assoc id entity)
        (update entity-type (fnil conj []) id))))

(defn apply-mutations
  "Some YTM responses include 'framework updates' which update a bunch of 'entities'
   by ID. This function takes a batch-update and returns a map of id -> entity"
  ([^js mutations] (apply-mutations {} mutations))
  ([initial-state ^js mutations]
   (loop [state initial-state
          mutations mutations]
     (if-let [mutation (first mutations)]
       (recur (apply-mutation state mutation)
              (next mutations))
       state))))

(defn- pick-thumbnail [entity]
  (when-let [thumb (first (j/get-in entity [:thumbnailDetails :thumbnails]))]
    (j/get thumb :url)))

(defn inflate-track [state track-id]
  (let [track-entity (get state track-id)]
    {:id (j/get track-entity :videoId)
     :title (j/get track-entity :title)
     :kind :track
     :provider :ytm
     :artist (j/get track-entity :artistNames)
     :image-url (pick-thumbnail track-entity)}))

(defn inflate-album [state album-id]
  (let [album-entity (get state album-id)
        details-entity (get state (j/get album-entity :details))]
    {:id (j/get album-entity :id)
     :title (j/get album-entity :title)
     :kind :album
     :provider :ytm
     ::entity album-entity
     :description (j/get details-entity :description)
     :artist (j/get album-entity :artistDisplayName)
     :radio-playlist-id (j/get album-entity :radioAutomixPlaylistId)
     :image-url (pick-thumbnail album-entity)
     :items (->> (j/get details-entity :tracks)
                 (mapv (partial inflate-track state)))}))

(defn- inflate-mutations [response]
  (when-let [mutations (j/get-in response [:frameworkUpdates
                                           :entityBatchUpdate
                                           :mutations])]
    (let [entities (apply-mutations mutations)
          album-id (->> entities :musicAlbumRelease first)]
      (when album-id
        (inflate-album entities album-id)))))

(defn- inflate-like-playlist [id response]
  (let [like-playlist (playlist/inflate id :album response)]
    (println "LIKE PL: " like-playlist)
    (when (seq (:items like-playlist))
      like-playlist)))

(defn load [^YTMusic client id]
  (p/let [response (send-request (.-cookie client)
                                 #js {:id id
                                      :type "ALBUM"
                                      :endpoint "browse"})]
    (or (inflate-mutations response)
        (inflate-like-playlist id response))))
