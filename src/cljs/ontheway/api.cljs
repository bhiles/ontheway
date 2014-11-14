(ns ontheway.api
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [ontheway.util :as u]))

;; Find directions (via Mapquest) 

(defn mapquest-directions-uri [to from transport-type]
  (let [query-params {"to" to
                      "from" from
                      "transport-type"  transport-type}
        uri (str (u/hostname) "/mapquest-directions")]
    (u/mk-uri uri query-params)))

(defn mapquest-directions [to from transport-type]
  (go
   (let [url (mapquest-directions-uri to from transport-type)
         response (<! (http/get url))]
     (:body response))))

;; Find Yelp businesses by a bounding box

(defn yelp-bounds-uri [map-bounds term]
  (let [query-params {"bounds" (str (:sw-lat map-bounds) ","
                                    (:sw-lng map-bounds) "|"
                                    (:ne-lat map-bounds) ","
                                    (:ne-lng map-bounds))
                      "term" term}
        uri (str (u/hostname) "/yelp-bounds")]
    (u/mk-uri uri query-params)))

(defn yelp-bounds [map-bounds category]
  (go
   (let [  yelp-response (<! (http/get (yelp-bounds-uri map-bounds category)))]
     (-> yelp-response :body))))


;; Find Yelp businesses that are nearby a route

(defn find-biz-uri [to from transport-type category]
  (let [query-params {"to" to
                      "from" from
                      "transport" transport-type
                      "term" category}
        uri (str (u/hostname) "/find-biz")]
    (u/mk-uri uri query-params)))

(defn find-biz [to from transport-type category]
  (go
   (let [response (<! (http/get (find-biz-uri to from transport-type category)))]
     (:body response))))


