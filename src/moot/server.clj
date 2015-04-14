(ns moot.server
  (:gen-class)
  (:import [java.util Date])
  (:require [clojure.string :as s]
            [clojure.tools.reader.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.util.request :as request]
            [ring.util.response :as response]))

(let [the-next-id (atom 0)]
  (defn get-next-id
  "Return the next unique ID."
  []
  (swap! the-next-id inc)))

(defn index-html
  "The bootstrap HTML for this application at URI with MAP-ID."
  [map-id uri]
  (letfn [(meta [key val] (str "  <meta name='" key "' content='" val "'>"))]
    (s/join "\n"
            ["<!DOCTYPE html>"
             "<html>"
             " <head>"
             "  <meta charset='utf-8'>"
             (meta "moot-map-id" map-id)
             (meta "moot-uri" uri)
             "  <link rel='icon' type='image/x-icon' href='/favicon.ico'>"
             "  <script type='text/javascript' src='/moot.js'></script>"
             "  <script type='text/javascript'"
             "          src='https://maps.googleapis.com/maps/api/js?v=3.3'>"
             "  </script>"
             " </head>"
             "</html>"])))

(comment
  "In operation @state is a map of map-id to the representation of a map."
  "Each map representation includes a :used timestamp and :all,"
  "where :all is a map of guy-id to guy state."
  {11 {:used 1426283563469,
       :all {9 {:id 20, :title "Mr Blue", :color :blue,
                :position {:lat 42.366223399999996, :lng -71.0912799}},
             1 {:id 1, :color :blue, :title "Mr Blue",
                :position {:lat 42.357465, :lng -71.095194}},
             2 {:id 2, :color :green, :title "Mr Green",
                :position {:lat 42.364251, :lng -71.1103}}}}
   13 {:used 1426283923384,
       :all {5 {:id 5, :color :orange, :title "Mr Orange",
                :position {:lat 42.369347, :lng -71.101107}},
             6 {:id 6, :color :pink, :title "Mr Pink",
                :position {:lat 42.365257, :lng -71.087246}}}}})

(def state
  "The participants in a map indexed by map-id."
  (atom nil))

(defn discard-old-maps
  "Discard all but the 9 most recently used maps."
  [state]
  (into {} (take 9 (sort-by (comp > :used) state))))

(defn mock-map
  "A new map with MAP-ID of mocked guys."
  [map-id]
  (let [mock [{:title "Mr Blue" :color :blue
               :position {:lat 42.357465 :lng -71.095194}}
              {:title "Mr Green" :color :green
               :position {:lat 42.364251 :lng -71.110300}}
              {:title "Mr LightBlue" :color :lightblue
               :position {:lat 42.364876 :lng -71.102352}}
              {:title "Mr Orange" :color :orange
               :position {:lat 42.369347 :lng -71.101107}}
              {:title "Mr Pink" :color :pink
               :position {:lat 42.365257 :lng -71.087246}}
              {:title "Mr Purple" :color :purple
               :position {:lat 42.361198 :lng -71.103983}}
              {:title "Mr Red" :color :red
               :position {:lat 42.372083 :lng -71.082062}}
              {:title "Mr Yellow" :color :yellow
               :position {:lat 42.366465 :lng -71.095194}}]
        all (into {} (for [guy mock :let [id (get-next-id)]]
                       [id (assoc guy :id id)]))]
    (swap! state assoc map-id {:all all})))

(def marker-icon-colors
  "The colors X available for map marker (x.png and x-dot.png) icons."
  (let [colors [:blue :green :red :yellow :orange :pink :purple]
        dotted (map #(keyword (str (name %) "-dot")) colors)]
    (reduce conj colors dotted)))

(defn get-guy-color-for-map
  "Return a color for GUY-ID in MAP-ID."
  [map-id guy-id]
  (let [state @state]
    (if-let [result (get-in state [map-id :all guy-id :color])]
      result
      (let [used (set (map :color (vals (get-in state [map-id :all]))))
            color (first (remove used marker-icon-colors))]
        (or color (first marker-icon-colors))))))

(defn update-map-guy!
  "Response to updating map with MAP-ID with guy keys."
  [map-id {:keys [id title position]}]
  #_(when (not (get @state map-id)) (mock-map map-id))
  (let [id (or id (get-next-id))
        color (get-guy-color-for-map map-id id)
        title (or title
                  (get-in @state [map-id :all id :title])
                  (str "Mr " (s/capitalize (name color))))
        guy {:id id :title title :color color :position position}]
    (let [now (.getTime (java.util.Date.))
          state (swap! state
                       #(-> %
                            (discard-old-maps)
                            (update-in [map-id] assoc :used now)
                            (update-in [map-id :all] assoc id guy)))]
      {:map-id map-id :you guy :all (get-in state [map-id :all])})))

(defn wrap-request-body-edn
  "Replace :body #HttpInput in handler with EDN when that is content-type."
  [handler]
  (fn [request]
    (if (= "application/edn" (request/content-type request))
      (handler (assoc request
                      :body (-> request
                                request/body-string
                                edn/read-string)))
      (handler request))))

(defn wrap-dump-request-response
  "Dump request and response maps."
  [handler]
  (fn [request]
    (let [update? (.startsWith (:uri request) "/update/")]
      (if update? (println (pr-str [:request request])))
      (let [response (handler request)]
        (if update? (println (pr-str [:response response])))
        response))))

(comment
  "A URI looks like this: /update/MAP-ID/ with the user's data in the POST."
  "Example: /update/909/"
  "POST" {:id 105 :title "Abe" :position {:lat 42.365257 :lng -71.087246}}
  "If the MAP-ID is 0 or if the MAP-ID is not found, make a new map."
  "Update" state "with the user's new location and name."
  "If no" :id "entry in user's update info, allocate a color to the user,"
  "and add the user's information to the map for MAP-ID."
  "Result is:" {MAP-ID (get-in @state MAP-ID)})

(defmacro do-or-nil
  "Result of BODY or nil if it throws."
  [& body]
  `(try (do ~@body) (catch Exception x# (println x#))))

(defn map-id-from-request
  "Return nil or the map ID parsed from request."
  [request]
  (let [re #"^/update/(\d+)\D?.*$"
        uri (:uri request)
        [update map-id] (re-find re (s/lower-case uri))]
    (or (do-or-nil
         (if (and update map-id)
           (edn/read-string map-id))))))

(defn respond-fail
  "Respond to a bad request."
  [request]
  (response/not-found (pr-str request)))

(defn respond-post
  "Respond to an update POST request."
  [request]
  (if-let [map-id (map-id-from-request request)]
    (let [state @state]
      (if (contains? state map-id)
        (let [body (update-map-guy! map-id (:body request))
              week (* 7 24 60 60)]
          (-> {:body (pr-str body)}
              (response/status 200)
              (response/content-type "application/edn")))
        (respond-fail request)))
    (respond-fail request)))

(defn respond-create
  "Respond by creating a new map with MAP-ID if necessary."
  [map-id]
  (let [now (.getTime (java.util.Date.))
        uri (str "/update/" map-id "/")]
    (swap! state update-in [map-id] assoc :used now)
    (-> (response/created uri (index-html map-id uri))
        (response/content-type "text/html"))))

(defn respond-get
  "Respond to a GET request."
  [request]
  (if-let [map-id (map-id-from-request request)]
    (let [state @state]
      (if (contains? state map-id)
        (respond-create map-id)
        (respond-fail request)))
    (respond-create (get-next-id))))

(defn handle-request
  "Return a response for REQUEST."
  [request]
  (println [:handle-request :cookies (:cookies request)])
  ((case (:request-method request)
     :get respond-get
     :post respond-post
     respond-fail) request))

(def moot-app
  "The server callback entry point when deployed."
  (-> handle-request
      (wrap-resource "/")
      wrap-cookies
      wrap-dump-request-response
      wrap-request-body-edn
      wrap-content-type
      wrap-not-modified
      wrap-params
      ))

(defn -main [& [port]]
  (let [port (Integer. (or port 8000))]
    (run-jetty #'moot-app {:port port :join? false})))

(comment "Evaluate this to start a new server in a REPL."
         (def server (-main 3000))
         )

(println [:RELOADED 'server])
