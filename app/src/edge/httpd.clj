;; Copyright © 2016, JUXT LTD.

(ns edge.httpd
  (:require
   [bidi.bidi :refer [tag]]
   [bidi.vhosts :refer [make-handler vhosts-model]]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   edge.phonebook.graphql
   [cheshire.core :as json]
   edge.yada.lacinia
   [edge.examples :refer [authentication-example-routes]]
   [edge.hello :refer [hello-routes other-hello-routes]]
   [edge.phonebook-app :refer [phonebook-app-routes]]
   [edge.phonebook.graphql :as graphql]
   [hiccup.core :refer [html]]
   [integrant.core :as ig]
   [schema.core :as s]
   [selmer.parser :as selmer]
   [yada.resources.classpath-resource :refer [new-classpath-resource]]
   [yada.resources.webjar-resource :refer [new-webjar-resource]]
   [yada.yada :refer [handler resource] :as yada]))

(defn content-routes []
  ["/"
   [
    ["public/" (assoc (new-classpath-resource "public") :id :edge.resources/static)]]])

(defn routes
  "Create the URI route structure for our application."
  [{:keys [edge.phonebook/db edge.graphql/schema edge/event-bus]
    :as config}]
  [""
   [
    ;; Document routes
    ["/" (yada/redirect (:edge.httpd/index config))]

    ["" (:edge.httpd/routes config)]

    ;; Hello World!
    (hello-routes)
    (other-hello-routes)

    (phonebook-app-routes config)

    (authentication-example-routes)

    ["/api" (-> (hello-routes)
                ;; Wrap this route structure in a Swagger
                ;; wrapper. This introspects the data model and
                ;; provides a swagger.json file, used by Swagger UI
                ;; and other tools.
                (yada/swaggered
                  {:info {:title "Hello World!"
                          :version "1.0"
                          :description "An API on the classic example"}
                   :basePath "/api"})
                ;; Tag it so we can create an href to this API
                (tag :edge.resources/api))]

    ;; Swagger UI
    ["/swagger" (-> (new-webjar-resource "/swagger-ui" {:index-files ["index.html"]})
                    ;; Tag it so we can create an href to the Swagger UI
                    (tag :edge.resources/swagger))]

    (graphql/routes config)

    ;; Our content routes, and potentially other routes.
    (content-routes)

    ;; This is a backstop. Always produce a 404 if we go there. This
    ;; ensures we never pass nil back to Aleph.
    [true (handler nil)]]])

;; TODO: Rename this package and component to listener
(defmethod ig/init-key :edge/httpd
  [_ {:edge.httpd/keys [host port] :as config}]
  (let [vhosts-model (vhosts-model [{:scheme :http :host host} (routes config)])
        listener (yada/listener vhosts-model {:port port})]
    (log/infof "Started http server on port %s" (:port listener))
    {:listener listener
     ;; host is used for announcement in dev
     :host host}))

(defmethod ig/halt-key! :edge/httpd [_ {:keys [listener]}]
  (when-let [close (:close listener)]
    (close)))
