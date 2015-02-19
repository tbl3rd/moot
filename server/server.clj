(ns server
  (:require [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]))

(defn handler
  [request]
  (println [:server/handler {:request request}])
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "Hello from Moot!"})

(def moot-app
  "The server callback entry point."
  (-> handler
      (wrap-resource "target")
      (wrap-file-info)))
