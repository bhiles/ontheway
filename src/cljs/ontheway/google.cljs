(ns ontheway.google)

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
