(ns ontheway.yelp
  (:require [ontheway.box :as b]
            [ontheway.util :as u]))

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

(defn biz-uri [map-bounds term]
  (let [query-params {"bounds" (str (:sw-lat map-bounds) ","
                                    (:sw-lng map-bounds) "|"
                                    (:ne-lat map-bounds) ","
                                    (:ne-lng map-bounds))
                      "term" term}
        uri (str (u/hostname) "/yelp-bounds")]
    (u/mk-uri uri query-params)))

(defn filter-and-rank-businesses [businesses lat-lngs]
  (let [relevant-biz (->> businesses
                          (filter-businesses-on-the-way lat-lngs)
                          sort-filter-businesses)
        numbered-biz (map #(assoc %1 :id %2)
                          relevant-biz
                          (iterate inc 1))]
    numbered-biz))
