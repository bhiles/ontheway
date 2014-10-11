(ns ontheway.core
  (:use [compojure.core]
        [clojure.string :only [join]]
        [clojure.walk :only [keywordize-keys]])
  (:require [clojure.data.json :as json]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ontheway.yelp :as yelp])
  (:import [java.net URLEncoder]))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

(defn url-encode [s]
  (URLEncoder/encode s))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))

(defn directions-uri [to from]
  (let [query-params {"origin" to
                      "destination" from
                      "mode" "walking"
                      "key" "AIzaSyB8xvy6nqjaVjJlmQc8lb_ZNVY4naSkQSA"}
        uri "https://maps.googleapis.com/maps/api/directions/json"]
    (mk-uri uri query-params)))


(defn direction-steps [to from]
  "Returns steps in the format
  [{:start-lat :start-lng :end-lat :end-lng}, ...]"
  (let [data (-> (directions-uri to from)
                 slurp
                 json/read-str
                 keywordize-keys)
        steps (-> data
                  :routes
                  first
                  :legs
                  first
                  :steps)
        lat-lngs (map
                  (fn [{:keys [start_location end_location]}]
                    (let [start-lat (start_location :lat)
                          start-lng (start_location :lng)
                          end-lat (end_location :lat)
                          end-lng (end_location :lng)]
                      {:start-lat start-lat
                       :start-lng start-lng
                       :end-lat end-lat
                       :end-lng end-lng}))
                  steps)]
    lat-lngs))

(defn distance-between
  "The distance between the two points (in miles) using the Haversine formula."
  [[lat1 lng1] [lat2 lng2]]
  (let [R 3963.1676 ; earth's radius (in miles)
        dlat (Math/toRadians (- lat2 lat1))
        dlng (Math/toRadians (- lng2 lng1))
        lat1 (Math/toRadians lat1)
        lat2 (Math/toRadians lat2)
        a (+ (* (Math/sin (/ dlat 2))
                (Math/sin (/ dlat 2)))
             (* (Math/sin (/ dlng 2))
                (Math/sin (/ dlng 2))
                (Math/cos lat1)
                (Math/cos lat2)))]
    (* R 2 (Math/asin (Math/sqrt a)))))

(defn find-box-corners [{:keys [start-lat start-lng end-lat end-lng]}]
  (let [extra 0.001
        sw-lat (- (min start-lat end-lat) extra)
        ne-lat (+ (max start-lat end-lat) extra)
        sw-lng (- (min start-lng end-lng) extra)
        ne-lng (+ (max start-lng end-lng) extra)]
    {:sw-lat sw-lat
     :sw-lng sw-lng
     :ne-lat ne-lat
     :ne-lng ne-lng}))

(defn within-box? [lat lng box]
  (let [{:keys [sw-lat sw-lng ne-lat ne-lng]} box]
    (and (< sw-lat lat ne-lat)
         (< sw-lng lng ne-lng))))

(defn find-businesses-on-the-way [steps businesses]
  (let [bounding-boxes (map find-box-corners steps)]
    (filter
     (fn [biz]
       (let [{:keys [latitude longitude]} (-> biz :location :coordinate)]
         (some
          (fn [box]
            (within-box? latitude longitude box))
          bounding-boxes)))
     businesses)))

(defroutes app-routes
  (GET "/" [] "<p>Hello from compojure</p>")
  (GET "/yelp" [] (json/write-str (yelp/fetch-businesses)))
  (route/resources "/")
  (route/not-found "Page not found"))

(def handler
  (handler/site app-routes))
