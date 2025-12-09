package com.drbrosdev

import io.ktor.http.HttpStatusCode
import kotlinx.html.*

fun HTML.createUrlPage(
    basePath: String,
) {
    headContent(pageTitle = "Y URL", basePath = basePath)

    yurlBody {
        mainLayout {
            p(classes = "text-xl sm:text-2xl text-center") { +"Yet Another URL Shortener" }
            postForm(
                encType = FormEncType.applicationXWwwFormUrlEncoded,
                action = "/create-url",
                classes = "w-full max-w-6xl flex flex-col items-center space-y-8"
            ) {
                input(
                    type = InputType.url,
                    classes = "w-full text-center text-2xl sm:text-4xl p-3 rounded focus:outline-hidden"
                ) {
                    // autocomplete="off" autocorrect="off" autocapitalize="off"
                    autoComplete = "off"
                    attributes["autocorrect"] = "off"
                    attributes["autocapitalize"] = "off"
                    required = true
                    name = "url"
                    id = "url"
                    placeholder = "Your URL here."
                }

                button(
                    classes = "w-full md:min-w-36 sm:w-auto border px-4 py-2 rounded hover:bg-onbrand hover:text-brand transition"
                ) { +"Go" }
            }
        }

        yurlFooter()
    }
}

fun HTML.shortUrlCreatedPage(
    basePath: String,
    createdUrl: String,
) {
    headContent(pageTitle = "Success!", basePath = basePath)

    yurlBody {
        mainLayout {
            h1(classes = "text-2xl sm:text-3xl") { +"Your short URL is" }
            a(
                href = createdUrl,
                target = null,
                classes = "text-2xl sm:text-3xl font-bold hover:underline visited:text-purple-600 break-all"
            ) {
                +createdUrl
            }

            goAgainAnchor(basePath)
        }

        yurlFooter()
    }
}

fun HTML.yurlErrorPage(code: HttpStatusCode, basePath: String) {
    headContent(pageTitle = "Something went wrong.", basePath = basePath)

    yurlBody {
        mainLayout {

            p(classes = "mb-4 text-sm font-semibold uppercase text-red-500 md:text-base") {
                +"That's a ${code.value}"
            }
            h1(classes = "mb-2 text-center text-2xl font-fold text-onbrand md:text-3xl") {
                +code.description
            }
            p(classes = "mb-12 max-w-screen-md text-center text-gray-500 md:text-lg") {
                +"We can't process that request."
            }

            goAgainAnchor(basePath)
        }

        yurlFooter()
    }
}

// =====
//

private fun FlowContent.goAgainAnchor(href: String) = a(
    href = href,
    target = null,
    classes = "border px-4 py-2 rounded hover:bg-onbrand hover:text-brand transition"
) { +"Go again" }

private fun FlowContent.yurlFooter() =
    footer(classes = "text-center p-4") {
        a(
            classes = "text-sm underline",
            href = "https://github.com/nikolaDrljaca/yurl"
        ) {
            +"View on GitHub"
        }
    }

private inline fun HTML.yurlBody(
    crossinline block: BODY.() -> Unit
) = body(
    classes = "min-h-screen flex flex-col font-mono bg-brand text-onbrand",
    block = block
)

private inline fun FlowContent.mainLayout(
    crossinline block: MAIN.() -> Unit
) = main(
    classes = "flex-grow container mx-auto flex flex-col items-center justify-center px-4 space-y-10",
    block = block
)

//==== head and meta

private fun HTML.headContent(
    basePath: String,
    pageTitle: String = "",
    block: HEAD.() -> Unit = {}
) {
    head {
        title { +pageTitle }
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        meta(charset = "UTF-8")
        meta(name = "description", content = "Yet another URL shortener.")
        link(rel = "icon", href = "/favicon.ico", type = "image/x-icon")
        link(rel = "canonical", href = basePath)
        script(src = "https://cdn.jsdelivr.net/npm/@tailwindcss/browser@4") { }

        style(type = "text/tailwindcss") {
            unsafe {
                raw(
                    """
              @theme {
                --color-brand: #222831;
                --color-onbrand: #DFD0B8;
              }
                """
                )
            }
        }

        // open-graph
        meta(property = "og:site_name", content = "Y URL")
        meta(property = "og:url", content = basePath)
        meta(property = "og:image", content = "$basePath/logo.png")
        meta(property = "og:description", content = "Yet another URL shortener.")
        meta(property = "og:title", content = "Y URL")
        // twitter
        meta(name = "twitter:title", content = "Y URL")
        meta(name = "twitter:description", content = "Yet another URL shortener.")
        meta(name = "twitter:image", content = "$basePath/logo.png")

        block()
    }
}

private fun FlowOrMetaDataOrPhrasingContent.meta(property: String, content: String) = meta {
    attributes["property"] = property
    this.content = content
}
