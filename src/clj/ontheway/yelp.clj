(ns ontheway.yelp
  (:use [clojure.string :only [join]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.data.json :as json]
            [gws.yelp.client :as yelp-client]
            [clojurewerkz.spyglass.client :as mem]
            [ontheway.box :as b]))

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
    (if val
      (-> val json/read-str clojure.walk/keywordize-keys)
      (let [new-val (yelp-client/business-search yelp-conn
                                                 {"offset" offset
                                                  "term" "food"
                                                  "location" "Portland, OR"
                                                  "sort" "0"})]
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

(defn yelp-api-bounds [bounds term offset]
  (yelp-client/business-search yelp-conn
                               {"offset" offset
                                "bounds" bounds
                                "term" term
                                "sort" "0"}))

(defn fetch-businesses-bounds [bounds term]
  (let [max-count 100]
    (loop [offset 0
           coll []]
      (let [response (yelp-api-bounds bounds term offset)
            businesses (:businesses response)]
        (if (or (empty? businesses) (>= (count coll) max-count))
          coll
          (recur (+ offset 20) (concat coll businesses)))))))

(defn filter-businesses-on-the-way [steps businesses]
  (let [bounding-boxes (map b/find-box-corners steps)]
    (filter
     (fn [biz]
       (let [{:keys [latitude longitude]} (-> biz :location :coordinate)]
         (some
          (fn [box]
            (b/within-box? latitude longitude box))
          bounding-boxes)))
     businesses)))

(defn sort-filter-businesses [businesses]
  (->> businesses
       (remove :is_closed)
       (sort-by (juxt :rating :review_count))
       reverse))

(defn map-bounds->yelp-bounds [map-bounds]
  (str (:sw-lat map-bounds) ","
       (:sw-lng map-bounds) "|"
       (:ne-lat map-bounds) ","
       (:ne-lng map-bounds)))

(defn find-and-rank-businesses [map-bounds lat-lngs category]
  (let [businesses (fetch-businesses-bounds (map-bounds->yelp-bounds map-bounds)
                                               category)
        relevant-biz (->> businesses
                          (filter-businesses-on-the-way lat-lngs)
                          sort-filter-businesses)
        numbered-biz (map #(assoc %1 :id %2)
                          relevant-biz
                          (iterate inc 1))]
    numbered-biz))




(comment
  ;; this is a helper to test yelp queries
  (defn yelp-query [offset]
           (yelp-client/business-search yelp-conn
                                        {"offset" offset
                                         "term" "food"
                                         ;;"bounds" "45.5229568,-122.6836258|45.5307952,-122.6812918"
                                         "location" "Pearl District, Portland, OR"
                                         "sort" "2"})))
