(ns ontheway.modern
  (:use [clojure.string :only [trim join]])
  (:use-macros [dommy.macros :only [deftemplate sel1 sel]])
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [put! chan <!]]
            [ontheway.config :as config]
            [goog.dom :as dom]
            [goog.events :as events]
            [blade :refer [L]]
            [dommy.core :as dommy]
            [ontheway.util :as u]
            [ontheway.google :as google]
            [ontheway.box :as b]
            [ontheway.mapquest :as mapquest]
            [ontheway.yelp :as yelp]))

(blade/bootstrap)

;; Declare constants

;; (def tile-url "https://{s}.tiles.mapbox.com/v3/{id}/{z}/{x}/{y}.png")
(def tile-url "http://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}")
(def mappy (-> L (.map "mappy")))
(def directions-layer (-> L (.layerGroup [])))
(def biz-layer (-> L (.layerGroup [])))
(def biz-layer-items (atom {}))
(def autocompleteFrom (google.maps.places.Autocomplete.
                       (dom/getElement "directions-from")))
(def autocompleteTo (google.maps.places.Autocomplete.
                     (dom/getElement "directions-to")))

(def my-lat)
(def my-lng)

;; set the more options collapser to not toggle
(.collapse (js/$ ".collapse") {:toggle false})

;; Allows handling Nodelist like a seq
(extend-type js/NodeList
  ISeqable
  (-seq [array] (array-seq array 0)))

;; Form fields

(defn from-query []
  (if-let [node (.getPlace autocompleteFrom)]
    (.-formatted_address node)
    (let [form-val (.-value (dom/getElement "directions-from"))]
      (if (u/empty-string? form-val)
        (str my-lat "," my-lng)
        form-val))))

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
  (->> "btn-go"
       dom/getElement
       (.create js/Ladda)
       .start))

(defn stop-spinner []
  (.stopAll js/Ladda))

;; HTML templates

(defn section-id [num]
  (str "biz-" num))

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
              {:href (google/maps-url start-point
                                      (-> biz
                                          :location
                                          :coordinate
                                          (select-keys [:latitude :longitude])
                                          vals)
                                      end-point)}
              "Directions"]]]]]]]]]]))

(deftemplate no-biz-template []
  [:div {:id "no-biz-text" :class "row"}
   [:h3 "No businesses found"]])

;; Expand/Collapse HTML

(defn expand-biz-sidebar []
  (.setAttribute (dom/getElement "map-container")
                 "class" "col-md-8 no-right-padding")
  (.setAttribute (dom/getElement "biz-container")
                 "class" "col-md-4"))

(defn hide-biz-sidebar []
  (.setAttribute (dom/getElement "map-container")
                 "class" "col-md-12")
  (.setAttribute (dom/getElement "biz-container")
                 "class" ""))
(defn clear-biz-sidebar []
  (let [sidebar (dom/getElement "biz-container")]
    (dom/removeChildren sidebar)))

(defn hide-more-options []
  (.collapse (js/$ ".collapse") "hide"))

;; Map configuration/alteration

(defn setup-map [m lat lng]
  (-> m (.setView [lat lng] 12))
  (-> L (.tileLayer tile-url {:maxZoom 16 ;; this is the limitation of the tile
                              :id "examples.map-i875mjb7"})
      (.addTo m)))

(defn reset-map [m map-bounds]
  (.fitBounds m
              [[(:sw-lat map-bounds) (:sw-lng map-bounds)]
               [(:ne-lat map-bounds) (:ne-lng map-bounds)]]))

(defn add-numbered-marker [layer lat lng num]
  (let [NumberedDivIcon (.-NumberedDivIcon js/L)
        marker (-> L (.marker [lat, lng]
                              {:icon (NumberedDivIcon. {:number (str num)})})
                   (.on "click" #(aset js/window "location"
                                       (str "#" (section-id num)))))
        new-layer (.addLayer layer marker)]
    (swap! biz-layer-items assoc num new-layer)))

(defn add-numbered-marker-active [layer lat lng num]
  (let [NumberedDivIconActive (.-NumberedDivIconActive js/L)
        marker (-> L (.marker [lat, lng]
                              {:icon (NumberedDivIconActive. {:number (str num)})})
                   (.on "click" #(aset js/window "location"
                                       (str "#" (section-id num)))))
        new-layer (.addLayer layer marker)]
    (swap! biz-layer-items assoc num new-layer)))

(defn remove-marker [layer num]
  (let [marker (get @biz-layer-items num)]
    (swap! biz-layer-items dissoc num)
    (.removeLayer layer marker)))

;; Draw directions/markers

(defn draw-directions [m directions]
  (let [{:keys [start-point end-point lines map-bounds]} directions
        start-circle (-> L (.circle (vec start-point) 8 {:color "green"}))
        end-circle (-> L (.circle (vec end-point) 8 {:color "red"}))
        lines (-> L (.polyline lines))]
    (doseq [l [start-circle end-circle lines]]
      (.addLayer directions-layer l))
    (.addTo directions-layer m)))

(defn draw-yelp-markers [m numbered-biz]
  (doseq [biz numbered-biz]
    (let [{:keys [id name url]} biz
          {:keys [latitude longitude]} (-> biz :location :coordinate)]
      (add-numbered-marker biz-layer latitude longitude id)))
  (.addTo biz-layer m))

;; Yelp sidebar

(defn create-biz-sidebar [start-point end-point numbered-biz]
  (dommy/append! (sel1 :#biz-container)
                 (biz-template start-point end-point numbered-biz))
  (doseq [biz numbered-biz]
    (let [{:keys [id name url]} biz
          {:keys [latitude longitude]} (-> biz :location :coordinate)
          mouseover (u/listen (dom/getElement
                               (section-id (:id biz))) "mouseover")
          mouseout (u/listen (dom/getElement
                              (section-id (:id biz))) "mouseout")]
      (go
       (while true
         (<! mouseover)
         (remove-marker biz-layer id)
         (add-numbered-marker-active biz-layer latitude longitude id)))
      (go
       (while true
         (<! mouseout)
         (remove-marker biz-layer id)
         (add-numbered-marker biz-layer latitude longitude id))))))

;; Yelp/Directions setup

(defn fetch-draw-directions [m to from transport-type category]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds] :as directions}
         (<! (mapquest/directions to from transport-type))]
     (draw-directions m directions)
     (reset-map m map-bounds)
     directions)))

(defn draw-yelp-info [m directions category]
  (go
   (let [{:keys [lat-lngs start-point end-point map-bounds]} directions]
     (let [numbered-biz (<! (yelp/find-and-rank-businesses
                             map-bounds lat-lngs category))]
       (if (empty? numbered-biz)
         (dommy/append! (sel1 :#map-text-container) (no-biz-template))
         (do
           (draw-yelp-markers m numbered-biz)
           (create-biz-sidebar start-point end-point numbered-biz)
           (expand-biz-sidebar)) ;; reduce map size to allow for biz sidebar
         (reset-map m map-bounds))))))

;; Full page rendering after button submit

(let [clicks (u/listen (dom/getElement "btn-go") "click")]
  (go (while true
        ;; wait for a click
        (<! clicks)
        ; start loading spinner
        (start-spinner)
        ;; clear existing map's directions
        (.clearLayers directions-layer)
        ;; clear existing business markers
        (.clearLayers biz-layer)
        ;; clear the sidebar
        (hide-biz-sidebar)
        (clear-biz-sidebar)
        ;; hide the more options expander
        (hide-more-options)
        ;; remove no businesses found text (if even there)
        (u/remove-node "no-biz-text")
        ;; clear text (if not already cleared)
        (u/remove-node "explanation")
        ;; form params, directions, and yelp info
        (let [to (to-query)
              from (from-query)
              transport-type (transportation-query)
              category (category-query)
              directions (<! (fetch-draw-directions mappy to from
                                                    transport-type category))]
          (<! (draw-yelp-info mappy directions category))
          ;; stop loading spinner
          (stop-spinner)
          ))))

(defn geolocation [position]
  (let [lng (.-longitude js/position.coords)
        lat (.-latitude js/position.coords)]
    (def my-lat lat)
    (def my-lng lng)
    (setup-map mappy lat lng)))

;; Main method
(.getCurrentPosition js/navigator.geolocation geolocation)
