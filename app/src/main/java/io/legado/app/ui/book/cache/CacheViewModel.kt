package io.legado.app.ui.book.cache

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.AppConfig
import io.legado.app.help.BookHelp
import io.legado.app.help.ContentProcessor
import io.legado.app.help.storage.BookWebDav
import io.legado.app.utils.*
import nl.siegmann.epublib.domain.Author
import nl.siegmann.epublib.domain.MediaType
import nl.siegmann.epublib.domain.Metadata
import nl.siegmann.epublib.domain.Resource
import nl.siegmann.epublib.epub.EpubWriter
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset


class CacheViewModel(application: Application) : BaseViewModel(application) {


    fun export(path: String, book: Book, finally: (msg: String) -> Unit) {
        execute {
            if (path.isContentScheme()) {
                val uri = Uri.parse(path)
                DocumentFile.fromTreeUri(context, uri)?.let {
                    export(it, book)
                }
            } else {
                export(FileUtils.createFolderIfNotExist(path), book)
            }
        }.onError {
            finally(it.localizedMessage ?: "ERROR")
        }.onSuccess {
            finally(context.getString(R.string.success))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun export(doc: DocumentFile, book: Book) {
//        val filename = "${book.name} b1y ${book.author}.txt"
//        DocumentUtils.delete(doc, filename)
//        DocumentUtils.createFileIfNotExist(doc, filename)?.let { bookDoc ->
//            val stringBuilder = StringBuilder()
//            context.contentResolver.openOutputStream(bookDoc.uri, "wa")?.use { bookOs ->
//                getAllContents(book) {
//                    bookOs.write(it.toByteArray(Charset.forName(AppConfig.exportCharset)))
//                    stringBuilder.append(it)
//                }
//            }
//            if (AppConfig.exportToWebDav) {
//                // 导出到webdav
//                val byteArray =
//                        stringBuilder.toString().toByteArray(Charset.forName(AppConfig.exportCharset))
//                BookWebDav.exportWebDav(byteArray, filename)
//            }
//        }
        val epubfilename = "${book.name} b1y ${book.author}.epub"
        DocumentUtils.delete(doc, epubfilename)

        DocumentUtils.createFileIfNotExist(doc, epubfilename)?.let { bookDoc ->
            // Create new Book
            val epubBook = nl.siegmann.epublib.domain.Book()
            val metadata: Metadata = epubBook.getMetadata()
            // Set the title
            metadata.addTitle(book.name)

            metadata.setLanguage("zh-CN")

            // Add an Author
            metadata.addAuthor(Author("aa","bb"))
            metadata.identifiers
            var feedNum = 1

            context.contentResolver.openOutputStream(bookDoc.uri, "wa")?.use { bookOs ->
                getAllContents(book) { body: String, title: String ->
                    //bookOs.write(it.toByteArray(Charset.forName(AppConfig.exportCharset)))
                    val html =
                            "<html xmlns=\"http://www.w3.org/1999/xhtml\">"+
                                    "<head><title>xx</title></head>"+
                                    "<body><h1>$title</h1>"+
                                    "<p>$body</p>"+
                                    "</body></html>"
                    epubBook.addSection(
                        "$title", Resource(
                            null,
                            html.toByteArray(Charsets.UTF_8), "${feedNum}.html",
                            MediaType(
                                "application/xhtml+xml", ".html"
                            )
                        )
                    )
                    feedNum++
                }
                val epub = EpubWriter()
                epub.write(epubBook, bookOs)
            }
        }
//        getSrcList(book).forEach {
//            val vFile = BookHelp.getImage(book, it.third)
//            if (vFile.exists()) {
//                DocumentUtils.createFileIfNotExist(
//                    doc,
//                    "${it.second}-${MD5Utils.md5Encode16(it.third)}.jpg",
//                    subDirs = arrayOf("${book.name}_${book.author}", "images", it.first)
//                )?.writeBytes(context, vFile.readBytes())
//            }
//        }
    }

    private suspend fun export(file: File, book: Book) {
        val filename = "${book.name} by ${book.author}.txt"
        val bookPath = FileUtils.getPath(file, filename)
        val bookFile = FileUtils.createFileWithReplace(bookPath)
        val stringBuilder = StringBuilder()

        val epubFilename = "${book.name} by ${book.author}.epub"
        val epubBookPath = FileUtils.getPath(file, epubFilename)


        // Create new Book
        val epubBook = nl.siegmann.epublib.domain.Book()
        val metadata: Metadata = epubBook.getMetadata()
        // Set the title
        metadata.addTitle(book.name)
        // Add an Author
        metadata.addAuthor(Author(book.author))
        getAllContents(book) { body: String, title: String ->
            bookFile.appendText(body, Charset.forName(AppConfig.exportCharset))
            val html =
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\">"+
                    "<head><title>xx</title></head>"+
                    "<body><h1>$title</h1>"+
                    "<p>$body</p>"+
                    "</body></html>"
            epubBook.addSection("Introduction", Resource(html.toByteArray(Charsets.UTF_8), ".html"))
            stringBuilder.append(body)
        }
        val epub = EpubWriter()
        epub.write(epubBook, FileOutputStream(epubBookPath))

        if (AppConfig.exportToWebDav) {
            val byteArray =
                stringBuilder.toString().toByteArray(Charset.forName(AppConfig.exportCharset))
            BookWebDav.exportWebDav(byteArray, filename) // 导出到webdav
        }
        getSrcList(book).forEach {
            val vFile = BookHelp.getImage(book, it.third)
            if (vFile.exists()) {
                FileUtils.createFileIfNotExist(
                    file,
                    "${book.name}_${book.author}",
                    "images",
                    it.first,
                    "${it.second}-${MD5Utils.md5Encode16(it.third)}.jpg"
                ).writeBytes(vFile.readBytes())
            }
        }
    }

    private suspend fun getAllContents(book: Book, append: (text: String,title: String) -> Unit) {
        val useReplace = AppConfig.exportUseReplace
        val contentProcessor = ContentProcessor(book.name, book.origin)
        //append("${book.name}\n${context.getString(R.string.author_show, book.author)}")
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            BookHelp.getContent(book, chapter).let { content ->
                val content1 = contentProcessor
                    .getContent(book, chapter.title, content ?: "null", false, useReplace)
                val content2=content1.subList(1,content1.size)
                    .joinToString("</p><p>")
                append.invoke("$content2",chapter.title)
            }
        }
    }

    private fun getSrcList(book: Book): ArrayList<Triple<String, Int, String>> {
        val srcList = arrayListOf<Triple<String, Int, String>>()
        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
            BookHelp.getContent(book, chapter)?.let { content ->
                content.split("\n").forEachIndexed { index, text ->
                    val matcher = AppPattern.imgPattern.matcher(text)
                    if (matcher.find()) {
                        matcher.group(1)?.let {
                            val src = NetworkUtils.getAbsoluteURL(chapter.url, it)
                            srcList.add(Triple(chapter.title, index, src))
                        }
                    }
                }
            }
        }
        return srcList
    }
}