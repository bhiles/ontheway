(defproject ontheway "0.1.0-SNAPSHOT"
  :description "A website the helps your find great places"
  :url "http://ontheway.bennetthiles.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [
                 ;; clojure
                 [org.clojure/clojure "1.5.1"]
                 [compojure "1.1.9"]
                 [clj-http "1.0.0"]
                 [org.clojure/data.json "0.2.5"]
                 [gws/yelp "0.2.0"]
                 [environ "1.0.0"]
                 
                 ;; clojurescript
                 [org.clojure/clojurescript "0.0-2234"]
                 [net.drib/blade "0.1.0"]
                 [prismatic/dommy "0.1.3"]
                 [cljs-http "0.1.16"]]
  
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.11"]]
  :ring {:handler ontheway.core/handler}
  :cljsbuild {:builds
              {:default
               {:source-paths ["src/cljs/ontheway"]
                 :compiler {:output-to "resources/public/js/core.js"
                            :optimizations :whitespace
                            :pretty-print true}}
               :mobile
               {:source-paths ["src/cljs/montheway"]
                :compiler {:output-to "resources/public/js/mobile-core.js"
                           :optimizations :whitespace
                           :pretty-print true}}}})
