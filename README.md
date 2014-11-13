# ontheway

A website that lets you enter where you are going and it finds places for you to go while you are on your way.  This most likely is a restaurant but could also be a park or any searchable place.

## Goal

Find places to eat/drink that are on your route.  Currently, Yelp lets you search a radius, but when driving a location > 10 miles away the radius search becomes useless, since it suggests places that are nowhere near your route.  This product addresses that problem, and only suggests places close to your route.  This application will not provide directions, it merely suggests places to go near your route.  

## Technologies

* Clojure
* Compojure (Web API)
* json library
* http library for clojurescript and clojure
* Yelp api lib
* Clojurescript
* Leaflet.js (Map)
* Bootstrap (CSS)
* Yelp API, Google Directions API, Mapquest API
* Google Autocomplete
* Ladda.js
* detectmobile.js

## Developing

Requirements 

*(clojure 1.5+)

Edit configuration files to enter in your API keys
lein ring server
lein cljsbuild auto default mobile

## Resources

- website
- mobile site
- [Uptime Robot](https://uptimerobot.com/) for monitoring

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
