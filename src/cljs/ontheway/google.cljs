(ns ontheway.google
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [ontheway.util :as u]
            [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]))

;; Use Google Directions API to find 

(defn directions-uri [to from]
  (let [query-params {"origin" to
                      "destination" from
                      "mode" "walking"
                      "key" "AIzaSyB8xvy6nqjaVjJlmQc8lb_ZNVY4naSkQSA"}
        uri "https://maps.googleapis.com/maps/api/directions/json"]
    (u/mk-uri uri query-params)))

(defn fetch-lat-lngs [to from]
  (go
   (let [url (u/proxy-url (directions-uri to from))
         response (<! (http/get url))
         steps (-> response
                   :body :routes first :legs first :steps)
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
     lat-lngs)))

;; Google driving directions webpage

(defn maps-url
  "Construct the URL for the google maps page with the start destination,
  the waypoint, and the final destination."
  [start way-point destination]
  (let [[start-lat start-lng] start
        [way-lat way-lng] way-point
        [dest-lat dest-lng] destination]
    (str "https://www.google.com/maps/dir/"
         start-lat "," start-lng "/"
         way-lat "," way-lng "/"
         dest-lat "," dest-lng "/")))

(defn mobile-maps-url [start destination]
  (let [[start-lat start-lng] start
        [dest-lat dest-lng] destination]
    (str "comgooglemaps://?saddr="
         start-lat "," start-lng "&daddr="
         dest-lat "," dest-lng)))
