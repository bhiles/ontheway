(ns montheway.mmodern
  (:use-macros [dommy.macros :only [deftemplate sel1 sel]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [dommy.core :as dommy]
            [ontheway.config :as config]))

(defn listen [el type]
  (let [out (chan)]
    (events/listen el type
                   (fn [e] (put! out e)))
    out))

(defn from-query []
  (.-value (dom/getElement "directions-from")))

(defn to-query []
  (.-value (dom/getElement "directions-to")))

(defn section-id [num]
  (str "biz-" num))

(defn google-maps-url [start way-point destination]
  (let [[start-lat start-lng] start
        [way-lat way-lng] way-point
        [dest-lat dest-lng] destination]
    (str "https://www.google.com/maps/dir/"
         start-lat "," start-lng "/"
         way-lat "," way-lng "/"
         dest-lat "," dest-lng "/")))

(deftemplate biz-template [start-point end-point businesses]
  (for [biz businesses]
    [:div {:class "row"}
     [:section {:id (section-id (:id biz))}
      [:div {:class "media"}
       [:a {:class "pull-left"}
        [:img
         {:class "media-object"
          :style "width: 180px; height: auto; overflow: hidden;"
          :src (:image_url biz)}]]
       [:div
        {:class "media-body"}
        [:h4
         [:a {:href (:url biz)}
          (str (:id biz) ". " (:name biz))]]
        [:table {:class "table table-condensed"}
         [:tbody
          [:tr
           [:td {:class "text-right"}
            "Categories"]
           [:td (->> (:categories biz)
                     (mapcat
                      (fn [c]
                        [(first c) [:br]])))]]
          [:tr
           [:td {:class "text-right"}
            "Rating"]
           [:td (:rating biz)]]
          [:tr
           [:td {:class "text-right"}
            "Reviews"]
           [:td (:review_count biz)]]
          [:tr
           [:td {:class "text-right"}]
           [:td
            [:small
             [:a
              {:href (google-maps-url start-point
                                      (-> biz
                                          :location
                                          :coordinate
                                          (select-keys [:latitude :longitude])
                                          vals)
                                      end-point)}
              "Directions"]]]]]]]]]]))

;; Allows handling Nodelist like a seq
(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

(defn url-encode [s]
  (js/encodeURIComponent s))

(defn mk-uri [base-uri query-params]
  (str base-uri "?"
       (clojure.string/join "&"
             (map (fn [[k v]] (str k "=" (url-encode v))) query-params))))

(defn proxy-url [url]
  (str config/hostname "/proxy?url=" (url-encode url)))

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

(defn sort-filter-businesses [businesses]
  (->> businesses
       (remove :is_closed)
       (sort-by (juxt :rating :review_count))
       reverse))

(defn json-parse [s]
  (js->clj
   (.parse js/JSON s)))

(defn fetch-google-lat-lngs [to from]
  (go
   (let [url (proxy-url (directions-uri to from))
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

(defn mapquest-uri [to from]
  (let [query-params {
                      "key" "Fmjtd|luurnuurn0,bl=o5-9wr5lf"
                      "to" to
                      "from" from
                      "routeType" "fastest"
                      "narrativeType" "none"
                      "shapeFormat" "raw"
                      "generalize" "0"
                      "outFormat" "json"}
        uri "http://www.mapquestapi.com/directions/v2/route"]
    (mk-uri uri query-params)))

(defn fetch-mapquest-lat-lngs [to from]
  (go
   (let [url (proxy-url (mapquest-uri to from))
         response (<! (http/get url))
         steps (-> response
                   :body :route :shape :shapePoints)
         lat-lngs (map
                   (fn [[start-lat start-lng end-lat end-lng]]
                     {:start-lat start-lat
                      :start-lng start-lng
                      :end-lat end-lat
                      :end-lng end-lng})
                   (partition 4 2 steps))]
     lat-lngs)))

(defn direction-steps [m to from]
  "Returns steps in the format
  [{:start-lat :start-lng :end-lat :end-lng}, ...]"
  (go
   (let [lat-lngs (<! (fetch-mapquest-lat-lngs to from))
         start-point (-> lat-lngs first (select-keys [:start-lat :start-lng]) vals)
         end-point (-> lat-lngs last (select-keys [:end-lat :end-lng]) vals)
         last-lat-lng (last lat-lngs)
         lines (concat
                (map
                 (fn [{:keys [start-lat start-lng]}]
                   [start-lat start-lng])
                 lat-lngs)
                [[(:end-lat last-lat-lng) (:end-lng last-lat-lng)]])
         map-bounds (max-box-corners lat-lngs)]
     ;; Fetch and draw Yelp data
     (let [yelp-url (str config/hostname "/yelp-bounds?bounds="
                                   (:sw-lat map-bounds) "," (:sw-lng map-bounds) "|"
                                   (:ne-lat map-bounds) "," (:ne-lng map-bounds))
           yelp-response (<! (http/get yelp-url))
           businesses (-> yelp-response :body)
           relevant-biz (->> businesses
                             (find-businesses-on-the-way lat-lngs)
                             sort-filter-businesses)
           numbered-biz (map #(assoc %1 :id %2)
                             relevant-biz
                             (iterate inc 1))]
       (dommy/append! (sel1 :#biz-container)
                      (biz-template start-point end-point numbered-biz))))))

(let [clicks (listen (dom/getElement "mobile-btn-go") "click")]
  (go (while true
        (<! clicks) ;; wait for a click
        (direction-steps nil (from-query) (to-query)) ;; draw map's directions
        )))
