(ns moot.server
  (:import [java.io InputStreamReader PushbackReader])
  (:require [clojure.string :as s]
            [clojure.tools.reader.edn :as edn]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response]
            [tailrecursion.boot.core :as boot]))

(defn body-edn
  "The body of REQUEST parsed as EDN."
  [request]
  (edn/read-string
   (slurp
    (java.io.PushbackReader.
     (java.io.InputStreamReader.
      (:body request))))))

(defn wrap-dump-uri
  "If REQUEST is for URI dump the request and response tagged with MSG."
  [handler uri msg]
  (fn [request]
    (let [response (handler request)]
      (if (= uri (s/lower-case (:uri request)))
        (println (pr-str {:msg msg :request request :response response})))
      response)))

(defn handle-request
  "Return a response for REQUEST."
  [request]
  (let [method (:request-method request)
        uri (s/lower-case (:uri request))
        params (:params request)]
    (cond (and (= method :post) (= uri "/update"))
          {:status  200
           :headers {"content-type" "text/plain"}
           :body (pr-str (body-edn request))}
          :else
          {:status 400
           :header {"content-type" "text/plain"}
           :body "Bad request."})))

(def moot-app
  "The server callback entry point."
  (-> handle-request
      (wrap-dump-uri "/update" :first)
      wrap-params
      (wrap-file "target" {:index-files? true})
      wrap-file-info                    ; works!
      (wrap-dump-uri "/update" :last)))

(println [:RELOADED 'server])
