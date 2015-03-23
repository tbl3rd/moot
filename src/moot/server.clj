(ns moot.server
  (:gen-class)
  (:import [java.io InputStreamReader PushbackReader]
           [java.util Date])
  (:require [clojure.string :as s]
            [clojure.tools.reader.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.mime-type :as mime-type]
            [ring.util.request :as request]
            [ring.util.response :as response]
            [tailrecursion.boot.core :as boot]))

(def the-next-id (atom 0))
(defn get-next-id [] (swap! the-next-id inc))

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
  [map-id guy-id]
  (let [state @state]
    (if-let [result (get-in state [map-id guy-id :color])]
      result
      (let [used (set (map :color (vals (get state map-id))))
            color (first (remove used marker-icon-colors))]
        (or color (first marker-icon-colors))))))

(defn update-map-guy
  "Update guy in map with MAP-ID."
  [map-id guy]
  (when (not (get @state map-id)) (mock-map map-id))
  (let [guy-id (or (:id guy) (get-next-id))
        color (get-guy-color-for-map map-id guy-id)
        title (or (:title guy) (str "Mr " (s/capitalize (name color))))
        position (:position guy)
        new-guy {:id guy-id :title title :color color :position position}]
    (let [now (.getTime (java.util.Date.))
          state (swap! state
                       #(-> %
                            (discard-old-maps)
                            (update-in [map-id] assoc :used now)
                            (update-in [map-id :all] assoc guy-id new-guy)))]
      (get-in state [map-id :all guy-id]))))

(defn wrap-index-html-response
  "Respond when request is implicitly for index.html."
  [handler]
  (fn [request]
    (if (= "/" (:uri request))
      (do (println [:wrap-index-html-response :request request])
          (-> (response/resource-response "index.html")
              (response/content-type "text/html")
              (response/status 200)))
      (handler request))))

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
  "Value of BODY or nil if it throws."
  [& body]
  `(try (do ~@body) (catch Exception x# (println x#))))

(defn map-id-from-uri
  "Return nil or the map ID parsed from and /update/ URI."
  [uri]
  (let [re #"^/update/(.+)/$"
        [update map-id] (re-find re (s/lower-case uri))]
    (do-or-nil
     (if (and update map-id)
       (let [id (edn/read-string map-id)]
         (if (and id (> id 0)) id))))))

(defn succeed
  "A success response to PATH with MAP-ID and YOU encoded in edn."
  [path map-id you]
  (let [week (* 7 24 60 60)
        value (select-keys you [:id :title :color])
        all (get-in @state [map-id :all])]
    (-> {:body (pr-str {:map-id map-id :you you :all all})}
        (response/status 200)
        (response/content-type "application/edn")
        (response/set-cookie (str map-id) value {:max-age week :path path}))))

(defn handle-request
  "Return a response for REQUEST."
  [request]
  (println [:handle-request :request request])
  (let [fail {:status 400 :body "Bad map request."}
        uri (:uri request)
        map-id (or (map-id-from-uri uri) (get-next-id))]
    (cond
      (and map-id (= :post (:request-method request)))
      (let [guy (doto (:body request) #(println [:dump %]))
            you (update-map-guy map-id guy)]
        (succeed uri map-id you))
      map-id
      (if-let [cv (get-in request [:cookies :value])]
        (let [you (select-keys cv [:id :title :color])
              you (update-map-guy map-id you)]
          (succeed uri map-id you))
        (succeed uri map-id (update-map-guy map-id {})))
      :else fail)))

(def moot-app
  "The server callback entry point when deployed."
  (-> handle-request
      (wrap-resource "/")
      wrap-dump-request-response
      wrap-index-html-response
      wrap-request-body-edn
      wrap-content-type
      wrap-not-modified
      wrap-params
      wrap-cookies))

(defn -main [& [port]]
  (let [port (Integer. (or port 8000))]
    (run-jetty #'moot-app {:port port :join? false})))

(println [:RELOADED 'server])
