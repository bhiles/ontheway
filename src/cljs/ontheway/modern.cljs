(ns ontheway.modern
  (:use-macros [dommy.macros :only [deftemplate sel1]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [blade :refer [L]]
            [dommy.core :as dommy]))

(blade/bootstrap)

;; Declare constants
;; (def tile-url "https://{s}.tiles.mapbox.com/v3/{id}/{z}/{x}/{y}.png")
(def tile-url "http://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")
(def mappy (-> L (.map "mappy")))

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
                   (fn [e] (put! out e)))
    out))

(defn from-query []
  (.-value (dom/getElement "directions-from")))

(defn to-query []
  (.-value (dom/getElement "directions-to")))

(defn remove-explanation-text []
  (.remove (dom/getElement "explanation")))

(deftemplate simple-template [cat]
    [:img {:src cat}])

(deftemplate biz-template [businesses]
  (for [biz businesses]
    [:div {:class "row"}
     [:div {:class "media"}
      [:a {:class "pull-left"}
       [:img
        {:class "media-object"
         :style "width: 180px; height: auto; overflow: hidden;"
         :src (:image_url biz)}]]
      [:div
       {:class "media-body"}
       [:h4 {:class "list-group-item-heading"}
        [:a {:href (:url biz)}
         (:name biz)]]
       [:table {:class "table table-condensed"}
        [:tbody
         [:tr
          [:td {:class "text-right"}
           "Categories"]
          [:td (-> biz :categories first first)]]
         [:tr 
          [:td {:class "text-right"}
           "Hood"]
          [:td (-> biz :location :neighborhoods second)]]
         [:tr
          [:td {:class "text-right"}
           "Rating"]
          [:td (:rating biz)]]
         [:tr
          [:td {:class "text-right"}
           "Reviews"]
          [:td (:review_count biz)]]
         [:tr
          [:td {:class "text-right"}
           "Price"]
          [:td (:review_count biz)]]]]]]]))

(defn expand-biz-sidebar []
  (.setAttribute (dom/getElement "map-container")
                 "class" "col-md-8 no-right-padding")
  (.setAttribute (dom/getElement "biz-container")
                 "class" "col-md-4"))


(defn setup-map [m lat lng]
  (-> m (.setView [lat lng] 12))
  (-> L (.tileLayer tile-url {:maxZoom 16 ;; this is the limitation of the tile
                              :attribution "from me!"
                              :id "examples.map-i875mjb7"})
      (.addTo m)))

(defn add-numbered-marker [lat lng num]
  (let [NumberedDivIcon (.-NumberedDivIcon js/L)]
    (-> L (.marker [lat, lng]
                   {:icon (NumberedDivIcon. {:number (str num)})})
        (.addTo mappy))))

(defn url-encode [s]
  (js/encodeURIComponent s))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (clojure.string/join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))

(defn directions-uri [to from]
  (let [query-params {"origin" to
                      "destination" from
                      "mode" "walking"
                      "key" "AIzaSyB8xvy6nqjaVjJlmQc8lb_ZNVY4naSkQSA"}
        uri "https://maps.googleapis.com/maps/api/directions/json"]
    (mk-uri uri query-params)))

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

(defn max-box-corners [steps]
  (let [extra 0.005 ;; extra leaves room for the top of the page (and bottom)
        lats (mapcat (juxt :start-lat :end-lat) steps)
        lngs (mapcat (juxt :start-lng :end-lng) steps)
        sw-lat (- (apply min lats) extra)
        ne-lat (+ (apply max lats) extra)
        sw-lng (- (apply min lngs) extra)
        ne-lng (+ (apply max lngs) extra)]
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

(defn json-parse [s]
  (js->clj
   (.parse js/JSON s)
   :keywordize-keys true))

(defn direction-steps [m to from]
  "Returns steps in the format
  [{:start-lat :start-lng :end-lat :end-lng}, ...]"
  (go
   (let [url (directions-uri to from)
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
                   steps)
         ;; TODO: missing last lat-lng
         last-lat-lng (last lat-lngs)
         lines (concat
                (map
                 (fn [{:keys [start-lat start-lng]}]
                   [start-lat start-lng])
                 lat-lngs)
                [[(:end-lat last-lat-lng) (:end-lng last-lat-lng)]])
         map-bounds (max-box-corners lat-lngs)]
     (-> L (.polyline lines)
         (.addTo m)) ;; draw map directions
     (.fitBounds m
                 [[(:sw-lat map-bounds) (:sw-lng map-bounds)]
                  [(:ne-lat map-bounds) (:ne-lng map-bounds)]])
     ;; Fetch and draw Yelp data
     (let [yelp-response (<! (http/get "http://localhost:3000/yelp"))
           businesses (-> yelp-response :body json-parse)
           relevant-biz (find-businesses-on-the-way lat-lngs businesses)]
       (doseq [biz relevant-biz]
         (let [{:keys [name url]} biz
               {:keys [latitude longitude]} (-> biz :location :coordinate)]
           (-> L (.marker [latitude, longitude]) 
               (.addTo m)
               (.bindPopup (str "<a href=\"" url "\">" name "</a>"))
               (.openPopup))))
       (dommy/append! (sel1 :#biz-container) (biz-template businesses))
       (expand-biz-sidebar) ;; reduce map size to allow for biz sidebar
       (.fitBounds m
                   [[(:sw-lat map-bounds) (:sw-lng map-bounds)]
                    [(:ne-lat map-bounds) (:ne-lng map-bounds)]])
       ))))

(let [clicks (listen (dom/getElement "btn-go") "click")]
  (go (while true
        (<! clicks) ;; wait for a click
        ;; clear existing map's directions
        (remove-explanation-text) ;; clear text (if not already cleared)
        (direction-steps mappy (from-query) (to-query)) ;; draw map's directions
        )))

(defn geolocation [position]
  (let [lng (.-longitude js/position.coords)
        lat (.-latitude js/position.coords)]
    (setup-map mappy lat lng)))

;; Main method
(.getCurrentPosition js/navigator.geolocation geolocation)
