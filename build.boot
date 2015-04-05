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
 :resource-paths #{"src"}
 :target-path "target"
 :dependencies '[[adzerk/boot-beanstalk "0.2.3"]
                 [adzerk/boot-cljs "0.0-2814-1"]
                 [adzerk/boot-cljs-repl "0.1.9"]
                 [adzerk/boot-reload "0.2.4"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3126"]
                 [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                 [ring "1.3.2"]
                 [tailrecursion/boot.core "2.5.1"]]
 :main-class 'moot.server)

(require '[adzerk.boot-beanstalk :refer [beanstalk dockerrun]]
         '[adzerk.boot-cljs :refer [cljs]]
         '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
         '[adzerk.boot-reload :refer [reload]]
         '[moot.server :refer [moot-app]]
         '[pandeiro.boot-http :as http])

(task-options!
 beanstalk {:access-key (System/getenv "MOOT_AWS_ACCESS_KEY")
            :secret-key (System/getenv "MOOT_AWS_SECRET_KEY")
            :version "0.1.0-SNAPSHOT"
            :beanstalk-envs [{:cname-prefix "moot" :name "moot-env"}]
            :deploy true
            :description "a meeting of gentlemen"
            :file "target/moot.war"
            :env "moot-env"
            :name "moot"}
 cljs {:output-to "moot.js"}
 war {:file "moot.war"}
 web {:serve 'moot.server/moot-app})

(deftask debug-client
  "Debug the moot client in a REPL with a reloading ring server."
  []
  (comp
   (http/serve :handler 'moot.server/moot-app :reload true)
   (cljs-repl)
   (watch :verbose true)
   (speak)
   (reload)
   (cljs :optimizations :none
         :source-map true
         :unified-mode true)))

(deftask debug
  "Debug the moot client and server in two separate REPLs."
  []
  (comp
   (repl)
   (cljs-repl)
   (watch :verbose true)
   (speak)
   (reload)
   (cljs :optimizations :none
         :source-map true
         :unified-mode true)))

(deftask build
  "Build the moot application uberwar file for Apache Tomcat."
  []
  (comp (speak)
        (cljs :optimizations :none
              :source-map true
              :unified-mode true)
        (web)
        (uber)
        (war)))

(deftask deploy
  "Deploy moot war file to Tomcat in AWS ElasticBeanstalk environment."
  []
  (comp (speak)
        (beanstalk)))
