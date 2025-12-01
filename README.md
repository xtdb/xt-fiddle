# XT-play

This repo holds all the code behind [XT play](https://play.xtdb.com/), a web tool for exploring the [XTDB database](https://xtdb.com).

It is deployed on an AWS Lambda but can be run locally as a normal web server for ease of development.

## Deploy

This repo is setup to deploy on push to `main`, so just merge your PR and go!

## Development

For the front end you'll need to install deps:

```sh
corepack enable && yarn install
```

After that for development you'll need two processes:

### Shadow-cljs

This can be started with:

```sh
npx shadow-cljs watch app
```

Now shadow-cljs will be watching for changes recompiling.
See the [shadow-cljs docs](https://shadow-cljs.github.io/docs/UsersGuide.html) for more.

### Clojure REPL

Start the REPL separately using `clj -M:dev:repl` (or similar). Make sure nothing else is conflicting with port 5432 (used for connecting to ephemeral XTDB instances over the pgwire protocol).

Then you can start the server by running `(go)` in the `user` namespace.

You should than be able to browse the cljs dev build at [http://localhost:8000](http://localhost:8000).

### Emacs development

```
M-x cider-jack-in-clj&cljs
```

Which command should be used (default clojure-cli): `shadow-cljs`

From the repl:

```
M-x cider-repl-set-ns user
```

Switch to namespace: `user`

```
user> (watch-deps!) (go!) (browse!)

```

## Troubleshooting

### Errors on shadow startup

<details>
<summary> java.lang.UnsupportedClassVersionError  </summary>

#### Error

```sh
Caused by: java.lang.UnsupportedClassVersionError: xtdb/api/Xtdb has been compiled by a more recent version of the Java Runtime (class file version 65.0), this version of the Java Runtime only recognizes class file versions up to 55.0
```
#### Solution
You're likely using the wrong java version for the project to run. Hint: the major version in the project is in the error, make sure you're running the correct java version before running.

---

</details>

<details>
<summary> -A:cljs:dev (No such file or directory)  </summary>

#### Error

```sh
Execution error (FileNotFoundException) at java.io.FileInputStream/open0 (FileInputStream.java:-2).
-A:cljs:dev (No such file or directory)
```

#### Solution
Check Clojure is installed and up to date

```sh
clojure -Sdescribe
```
output:
```txt
{:version "1.12.0.1488"
 :config-files ["/usr/local/lib/clojure/deps.edn" "/home/juxt/.clojure/deps.edn" "deps.edn" ]
 :config-user "/home/juxt/.clojure/deps.edn"
 :config-project "deps.edn"
 :install-dir "/usr/local/lib/clojure"
 :config-dir "/home/juxt/.clojure"
 :cache-dir ".cpcache"
 :force false
 :repro false
 :main-aliases ""
 :repl-aliases ""}
```

---

</details>

### Cljs development errors

<details>
<summary>
shadow-cljs - Stale Output! Your loaded JS was not produced by the running shadow-cljs instance. Is the watch for this build running?
</summary>

If the watch did not start properly, you can manually start it by navigating to [http://localhost:9630/builds](http://localhost:9630/builds) and selecting "Watch".
Return to the running XTDB Play and refresh the page.
Hot reloads should now be working.

If this doesn't work, try a hard refresh or clearing site data.

</details>


## Infrastructure

Infrastructure is spread across five(!) files, all in the `cloudformation` folder:

| File | Description |
| --- | --- |
| `00-github-keys.yml` | Contains the user used by github to deploy |
| `01-certificate.yml` | Creates the certificate used by CloudFront (must be deployed in `us-east-1`) |
| `02-lambda-deps.yml` | Contains the things the lambda needs to deploy (an s3 bucket and role |
| `03-lambda.yml` | The lambda itself, in a separate file so github can deploy just that |
| `04-domain.yml` | The stuff needed to address the lambda via a static url |

These are glued together by liberal use of ssm parameters and [dynamic references](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/dynamic-references.html).

On initial deployment they *must* be deployed in the below order.

<details>

<summary>Initial creation</summary>

> [!NOTE]
> Wait for each step to finish deploying before deploying the next stage

```sh
aws cloudformation create-stack \
    --capabilities CAPABILITY_IAM \
    --stack-name xt-play--github \
    --template-body "file://$(pwd)/cloudformation/00-github-keys.yml"
```

Make sure to add the access_key_id and secret_access_key to the github actions secrets.

```sh
aws cloudformation create-stack \
    --region us-east-1 \
    --capabilities CAPABILITY_IAM \
    --stack-name xt-play--certificate \
    --template-body "file://$(pwd)/cloudformation/01-certificate.yml" \
    --parameters ParameterKey=HostedZoneId,ParameterValue=<hostedZoneId> \
                 ParameterKey=HostedZoneName,ParameterValue=<hostedZoneName>
```

```sh
aws cloudformation create-stack \
    --capabilities CAPABILITY_IAM \
    --stack-name xt-play--lambda-deps \
    --template-body "file://$(pwd)/cloudformation/02-lambda-deps.yml"
```

Before running this next step, upload the code to the freshly created s3 bucket.
Look in `03-lambda.yml` for the location.

(Updates to this will mainly be done by github)
```sh
aws cloudformation create-stack \
    --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
    --stack-name xt-play--lambda \
    --template-body "file://$(pwd)/cloudformation/03-lambda.yml" \
    --parameters ParameterKey=PlayCodeVersion,ParameterValue=<versionId>
```

The `certificateArn` here is from 01-certificate.yml, it can't be an ssm parameter due to being in a different region :/
```sh
aws cloudformation create-stack \
    --capabilities CAPABILITY_IAM \
    --stack-name xt-play--domain \
    --template-body "file://$(pwd)/cloudformation/04-domain.yml" \
    --parameters ParameterKey=HostedZoneId,ParameterValue=<hostedZoneId> \
                 ParameterKey=HostedZoneName,ParameterValue=<hostedZoneName> \
                 ParameterKey=CertificateArn,ParameterValue=<certificateArn>
```

> [!NOTE]
> To run an update just swap out `create-stack` for `update-stack`
>
> To delete a stack either use the AWS Console or run:
> ```sh
> aws cloudformation delete-stack --stack-name <stack-name>
> ```

</details>
