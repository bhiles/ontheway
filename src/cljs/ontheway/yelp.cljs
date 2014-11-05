(ns ontheway.yelp
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [ontheway.config :as config]
            [ontheway.box :as b]
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
        uri (str config/hostname "/yelp-bounds")]
    (u/mk-uri uri query-params)))

(defn find-and-rank-businesses [map-bounds lat-lngs category]
  (go
   (let [yelp-response (<! (http/get (biz-uri map-bounds category)))
         businesses (-> yelp-response :body)
         relevant-biz (->> businesses
                           (filter-businesses-on-the-way lat-lngs)
                           sort-filter-businesses)
         numbered-biz (map #(assoc %1 :id %2)
                           relevant-biz
                           (iterate inc 1))]
       numbered-biz)))
