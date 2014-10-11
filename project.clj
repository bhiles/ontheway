(defproject ontheway "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/clj" "src/cljs"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2234"]
                 [compojure "1.1.9"]
                 [org.clojure/data.json "0.2.5"]
                 [clojurewerkz/spyglass "1.1.0"]
                 [net.drib/blade "0.1.0"]
                 [gws/yelp "0.2.0"]
                 [cljs-http "0.1.16"]]
  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-ring "0.8.11"]]
  :ring {:handler ontheway.core/handler}
  :cljsbuild {:builds
              [{:source-paths ["src/cljs"]
                :compiler {:output-to "resources/public/js/modern.js"
                           :optimizations :whitespace
                           :pretty-print true}}]})
