;;234567890123456789012345678901234567890123456789012345678901234567890123456789
;;
(comment
  "Execute the following elisp to start a Clojure boot REPL."
  "This works around CIDER's assumption that lein is boss."
  ((lambda ()
           (set-variable 'cider-lein-command "boot")
           (set-variable 'cider-lein-parameters "repl")
           (cider-jack-in)))
  "First time: Put the cursor before this string and: C-xC-e"
  "After that use C-cM-j (AKA cider-jack-in) to restart boot REPL."

  "If not hacking build.boot, shell out and run: boot debug"
  "... to start up the development environment."
  "Then from Emacs visiting a Clojure buffer: M-x cider-connect"
  "Accept the cider-connect defaults with: RET RET RET."
  "Start a browser repl at the cider-repl prompt with: (start-repl)")

;; See: https://github.com/boot-clj/boot/wiki/Boot-Environment
;;
(set-env!
 :source-paths #{"src"}
 :resource-paths #{"src"}
 :target-path "target"
 :dependencies '[[adzerk/boot-beanstalk "0.2.3"]
                 [adzerk/boot-cljs "0.0-2814-1"]
                 [adzerk/boot-cljs-repl "0.1.9"]
                 [adzerk/boot-reload "0.2.4"]
                 [org.clojure/clojure "1.6.0"]
                 [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                 [ring "1.3.2"]
                 [tailrecursion/boot.core "2.5.1"]]
 :main-class 'moot.server)

(defn import-environment-variables
  "Add value ${FOO_BAR} to (get-env) for each key :foo-bar in keywords."
  [& keywords]
  (doall (map set-env! keywords
              (map (comp #(System/getenv %)
                         clojure.string/upper-case
                         #(clojure.string/replace % "-" "_")
                         name)
                   keywords)))
  (get-env))

#_(import-environment-variables :google-api-key
                                :moot-aws-access-key
                                :moot-aws-secret-key)

(require '[adzerk.boot-beanstalk :refer [beanstalk dockerrun]]
         '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[adzerk.boot-reload :refer [reload]]
         '[pandeiro.boot-http :as http])

(task-options!
 war {:file "moot.war"}
 web {:serve 'moot.server/moot-app}
 beanstalk {:access-key (System/getenv "MOOT_AWS_ACCESS_KEY")
            :secret-key (System/getenv "MOOT_AWS_SECRET_KEY")
            :description "a meeting of gentlemen"
            :file "moot.war"
            :env "moot"
            :name "moot"
            :beanstalk-envs [{:name "moot" :cname-prefix "moot"}]
            :version "0.1.0-SNAPSHOT"})

(deftask debug
  "Debug the moot client and server."
  []
  (comp
   (http/serve :handler 'moot.server/moot-debug :reload true)
   (watch :verbose true)
   (cljs-repl)
   (speak)
   (reload)
   (cljs :optimizations :none
         :output-to "moot.js"
         :source-map true
         :unified-mode true)))

(deftask build-tomcat
  "Build my application uberwar file."
  []
  (comp #_(cljs :optimizations :none
                :output-to "moot.js"
                :source-map true
                :unified-mode true)
        (web) (uber) (war)))

(deftask build-docker
  "Build my application docker zip file."
  []
  (comp (add-repo) (dockerrun) (zip)))

(deftask deploy-tomcat
  "Deploy application war file to AWS EB environment."
  []
  (task-options!
   beanstalk
   {:stack-name "Tomcat 8 Java 8 on 64bit Amazon Linux 2014.09 v1.2.0"
    :deploy true :env "moot"})
  (beanstalk))

(deftask deploy-docker
  "Deploy application docker zip file to AWS EB environment."
  []
  (task-options!
   beanstalk
   {:stack-name "Docker 1.3.3 on 64bit Amazon Linux 2014.09 v1.2.0."})
  identity)

(deftask oops
  "Say Oops"
  [x exclaim bool "Add an !"]
  (println (str "Oops" (if exclaim "!" "."))))
