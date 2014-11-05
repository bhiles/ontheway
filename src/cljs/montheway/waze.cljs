(ns montheway.waze)

(defn mobile-maps-url [dest]
  (let [[dest-lat dest-lng] dest]
    (str "waze://?navigate=yes&ll=" dest-lat "," dest-lng )))
