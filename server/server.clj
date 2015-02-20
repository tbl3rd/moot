(ns server
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
  [request]
  (edn/read
   (java.io.PushbackReader.
    (java.io.InputStreamReader.
     (:body request)))))

(defn wrap-println
  [handler]
  (fn [request]
    (let [response (handler request)]
      (if (= "/update" (s/lower-case (:uri request)))
        (println (pr-str {:request request
                          :response response
                          :body (body-edn request)
                          })))
      response)))

(defn handler
  [request]
  (let [method (:request-method request)
        uri (s/lower-case (:uri request))
        params (:params request)]
    (cond (and (= method :post) (= uri "/update"))
          {:status  200
           :headers {"content-type" "text/plain"}
           :body "Hello!"})))

(def moot-app
  "The server callback entry point."
  (-> handler
      ;; (wrap-reload {:dirs ["server"]})
      wrap-params
      (wrap-file "target" {:index-files? true})
      wrap-file-info                    ; works!
      wrap-println))

(println [:RELOADED 'server])
