(ns ontheway.yelp
  (:use [clojure.string :only [join]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.data.json :as json]
            [gws.yelp.client :as yelp-client]
            [clojurewerkz.spyglass.client :as mem]))

(def memcache-conn (mem/bin-connection "127.0.0.1:21211"))

(def yelp-conn (yelp-client/create
                "G5j9Jhe9v4begxf1QmWEQQ"
                "Nc2c8UNBoYYmqqQbF5DIXp0-Ms8"
                "SRy1O5wuwgNB-4wSTRTod7SdVeJzOifj"
                "me9S_ctqHoNQClIhxb0zeNpIq3Y"))

(defn yelp-api [offset]
  ;; TODO: fix key so that it includes query params since it's
  ;; hard-coded to Portland, OR
  (let [key (str "yelp-api-search-1" offset)
        val (mem/get memcache-conn key)]
    (prn "key is " key)
    (if val
      (do
        (prn "read from cache")
        (-> val json/read-str clojure.walk/keywordize-keys))
      (let [new-val (yelp-client/business-search yelp-conn
                                        {"offset" offset
                                         "term" "food"
                                         "location" "Portland, OR"
                                         "sort" "0"})]
        (prn "fetching val and saving it")
        (mem/set memcache-conn key 0 (json/write-str new-val))
        new-val))))

(defn fetch-businesses []
  (loop [offset 0
         coll []]
    (let [response (yelp-api offset)
          businesses (:businesses response)]
      (if (empty? businesses)
        coll
        (recur (+ offset 20) (concat coll businesses))))))
