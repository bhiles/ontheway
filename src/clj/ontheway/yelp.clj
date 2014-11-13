(ns ontheway.yelp
  (:require [gws.yelp.client :as yelp-client]
            [ontheway.box :as b]
            [ontheway.config :as c]))

(def yelp-conn (yelp-client/create
                c/yelp-consumer-key
                c/yelp-consumer-secret
                c/yelp-token
                c/yelp-token-secret))

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
