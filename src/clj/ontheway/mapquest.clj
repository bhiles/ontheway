(ns ontheway.mapquest
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [ontheway.box :as b]
            [ontheway.util :as u]
            [ontheway.config :as c]))

(defn route-type [option]
  (case option
    "driving" "fastest"
    "walking" "pedestrian"
    "biking" "bicycle"))

(defn directions-uri [to from transport-type]
  (let [query-params {
                      "key" c/mapquest-key
                      "to" to
                      "from" from
                      "routeType" (route-type transport-type)
                      "narrativeType" "none"
                      "shapeFormat" "raw"
                      "generalize" "0"}
        uri "http://www.mapquestapi.com/directions/v2/route"]
    (u/mk-uri uri query-params)))

(defn fetch-lat-lngs [to from transport-type]
  (let [url (directions-uri to from transport-type)
        response (http/get url)
        steps (-> response
                  :body (json/read-str :key-fn keyword) :route :shape :shapePoints)
        lat-lngs (map
                  (fn [[start-lat start-lng end-lat end-lng]]
                    {:start-lat start-lat
                     :start-lng start-lng
                     :end-lat end-lat
                     :end-lng end-lng})
                  (partition 4 2 steps))]
    lat-lngs))

(defn directions [to from transport-type]
  (let [lat-lngs (fetch-lat-lngs to from transport-type)
        start-point (-> lat-lngs first ((juxt :start-lat :start-lng)))
        end-point (-> lat-lngs last ((juxt :end-lat :end-lng)))
        last-lat-lng (last lat-lngs)
        lines (concat
               (map
                (fn [{:keys [start-lat start-lng]}]
                  [start-lat start-lng])
                lat-lngs)
               [[(:end-lat last-lat-lng) (:end-lng last-lat-lng)]])
        map-bounds (b/max-box-corners lat-lngs)]
    {:lat-lngs lat-lngs
     :start-point start-point
     :end-point end-point
     :lines lines
     :map-bounds map-bounds}))
