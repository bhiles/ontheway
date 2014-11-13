(ns montheway.core
  (:use [clojure.string :only [split]])
  (:use-macros [dommy.macros :only [deftemplate sel1 sel]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [goog.dom :as dom]
            [goog.events :as events]
            [dommy.core :as dommy]
            [ontheway.config :as config]
            [ontheway.util :as u]
            [ontheway.google :as google]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]
            [montheway.waze :as waze]))

;; Setup

(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

;; Form fields

(def autocompleteFrom (google.maps.places.Autocomplete.
                       (dom/getElement "directions-from")))
(def autocompleteTo (google.maps.places.Autocomplete.
                       (dom/getElement "directions-to")))

(defn from-query [lat lng]
  (if-let [node (.getPlace autocompleteFrom)]
    (.-formatted_address node)
    (let [form-val (.-value (dom/getElement "directions-from"))]
      (if (u/empty-string? form-val)
        (str lat "," lng)
        form-val))))

(defn from-query-current-location? []
  (u/empty-string? (.-value (dom/getElement "directions-from"))))

(defn to-query []
  (if-let [node (.getPlace autocompleteTo)]
    (.-formatted_address node)
    (.-value (dom/getElement "directions-to"))))

(defn category-query []
  (let [category (.-value (dom/getElement "directions-category"))]
    (if (u/empty-string? category)
      "food"
      category)))

(defn transportation-query []
  (.-value (dom/getElement "directions-transportation")))

(defn start-spinner []
  (->> "mobile-btn-go"
       dom/getElement
       (.create js/Ladda)
       .start))

(defn stop-spinner []
  (.stopAll js/Ladda))

;; HTML templates

(defn section-id [num]
  (str "biz-" num))

(defn waze-maps-url-template [dest-text dest]
  (if (from-query-current-location?)
    [:p
     [:small
      [:a
       {:href (waze/mobile-maps-url dest )}
       (str "Waze directions to " dest-text)]]]))

(deftemplate biz-template [start-point end-point businesses]
  (for [biz businesses]
    (let [id (section-id (:id biz))
          yelp-url (str "yelp:///biz/" (last (split (:url biz) #"/")))
          categories (mapcat
                      (fn [c]
                        [(first c) [:br]])
                      (:categories biz))
          [lat lng] (-> biz :location :coordinate
                        ((juxt :latitude :longitude)))
          waypoint [lat lng]
          distance (u/distance-between start-point waypoint)
          rounded-distance (.toFixed distance 2)
          gmaps-url-to-waypoint (google/mobile-maps-url start-point waypoint)
          gmaps-url-to-destination (google/mobile-maps-url waypoint end-point)
          waze-url-to-waypoint (waze-maps-url-template "way-point" waypoint)
          waze-url-to-destination (waze-maps-url-template "destination" end-point)]
      [:div {:class "row"}
       [:section {:id id}
        [:div {:class "media"}
         [:a {:class "pull-left"}
          [:img
           {:class "media-object"
            :style "width: 180px; height: auto; overflow: hidden;"
            :src (:image_url biz)}]]
         [:div
          {:class "media-body"}
          [:h4
           [:a {:href yelp-url}
            (str (:id biz) ". " (:name biz))]]
          [:table {:class "table table-condensed"}
           [:tbody
            [:tr
             [:td {:class "text-right"}
              "Categories"]
             [:td categories]]
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
              "Distance"]
             [:td (str rounded-distance " miles")]]]]
          [:p
           [:small
            [:a
             {:href gmaps-url-to-waypoint}
             "Google directions to way-point"]]]
          [:p
           [:small
            [:a
             {:href gmaps-url-to-destination}
             "Google directions to destination"]]]
          waze-url-to-waypoint
          waze-url-to-destination]]]])))

(deftemplate no-biz-template []
  [:h3 "No businesses found"])

;; Collapse HTML

(defn clear-biz-sidebar []
  (let [sidebar (dom/getElement "biz-container")]
    (dom/removeChildren sidebar)))

;; Display Yelp businesses

(defn biz-uri [to from transport-type category]
  (let [query-params {"to" to
                      "from" from
                      "transport" transport-type
                      "term" category}
        uri (str config/hostname "/find-biz")]
    (u/mk-uri uri query-params)))

(defn fetch-businesses [to from transport-type category]
  (go
   (let [response (<! (http/get (biz-uri to from transport-type category)))]
     (:body response))))

(defn display-businesses [to from transport-type category]
  (go
   (let [{:keys [start-point end-point businesses]}
             (<! (fetch-businesses to from transport-type category))]
     (dommy/append! (sel1 :#biz-container)
                    (if (empty? businesses)
                      (no-biz-template)
                      (biz-template start-point end-point businesses))))))

(defn display-businesses-client-side [to from transport-type category]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds] :as directions}
             (<! (mapquest/directions to from transport-type))
         numbered-biz (<! (yelp/find-and-rank-businesses
                           map-bounds lat-lngs category))]
     (dommy/append! (sel1 :#biz-container)
                    (if (empty? numbered-biz)
                      (no-biz-template)
                      (biz-template start-point end-point numbered-biz))))))

;; Page rendering logic after button submit

(defn setup-button-click [lat lng]
  (let [clicks (u/listen (dom/getElement "mobile-btn-go") "click")]
    (go (while true
          (<! clicks) ;; wait for a click
          (start-spinner)
          (clear-biz-sidebar)
          (<! (display-businesses (to-query) (from-query lat lng) 
                                  (transportation-query) (category-query)))
          (stop-spinner)))))

;; Main method

(go 
 (let [coords    (.-coords (<! (u/get-position)))
       latitude  (.-latitude coords)
       longitude (.-longitude coords)]
   (setup-button-click latitude longitude)))
