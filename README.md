# On The Way

A website that lets you enter where you are going and it finds places for you to go while you are on your way.  This most likely is a restaurant but could also be a park or any searchable place.

## Goal

Find places to eat/drink that are on your route.  Currently, Yelp lets you search a radius, but when driving a location > 10 miles away the radius search becomes useless, since it suggests places that are nowhere near your route.  This product addresses that problem, and only suggests places close to your route.  This will not provide directions, it merely suggests places to go near your route, but it does offer links to get directions.

## How it works

You enter where you are going and directions are fetched from Mapquest.  Those directions are composed of a list of latitudes and longitudes.  Grouping two consecutive latitude/longitude points allows you to have a starting point and and ending point.  Drawing all these start/end points together lets you draw a route.  But more importantly, taking a start/end point you can draw a box around that section of the route.  Yelp allows you to search businesses by using a box like this, so next we fetch a list businesses from them.  Finally, we display the route and the businesses.

## Technologies

* Clojure
* Clojurescript
* Leaflet.js
* Bootstrap
* Yelp API
* Mapquest API
* additional [clojure libs](project.clj)
* additional [javascript libs](resources/public/js)

## Developers

### Requirements 

* Lein
* Clojure
* [Mapquest API key](http://developer.mapquest.com/)
* [Yelp API access](http://www.yelp.com/developers/manage_api_keys)

### Running

Edit [configuration file](.lein-env) to enter in your API keys.

Convert the Clojurescript to Javascript.

    lein cljsbuild auto default mobile

Start the ring server

    lein ring server

[Load the application in a brower](http://localhost:3000)

## Additional Resources

* [Website](http://bennetthiles.com/map.html)
- [Mobile website](http://bennetthiles.com/mmap.html)
- [Uptime Robot](https://uptimerobot.com/) - used for monitoring

## License

Copyright © Bennett Hiles

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
