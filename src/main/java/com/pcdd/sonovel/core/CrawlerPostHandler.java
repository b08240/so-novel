package com.pcdd.sonovel.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileAppender;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.setting.dialect.Props;
import com.pcdd.sonovel.model.NovelInfo;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author pcdd
 */
@UtilityClass
public class CrawlerPostHandler {

    private static final String SAVE_PATH;

    static {
        Props p = Props.getProp("config.properties", StandardCharsets.UTF_8);
        SAVE_PATH = p.getStr("savePath");
    }

    public void handle(String extName, NovelInfo novelInfo, File saveDir) {
        switch (extName) {
            case "txt":
                Console.log("\n<== 下载完毕，开始合并 txt");
                mergeTxt(saveDir, novelInfo.getBookName(), novelInfo.getAuthor());
                break;
            case "epub":
                Console.log("\n<== 下载完毕，开始转换为 epub");
                convertToEpub(saveDir, novelInfo);
                break;
            default:
        }
    }

    @SneakyThrows
    private void convertToEpub(File dir, NovelInfo novelInfo) {
        Book book = new Book();
        book.getMetadata().addTitle(novelInfo.getBookName());
        book.getMetadata().addAuthor(new Author(novelInfo.getAuthor()));
        book.getMetadata().addDescription(novelInfo.getDescription());
        byte[] bytes = HttpUtil.downloadBytes(novelInfo.getCoverUrl());
        book.setCoverImage(new Resource(bytes, ".jpg"));

        int i = 0;
        // 遍历下载后的目录，添加章节
        for (File file : files(dir)) {
            // 截取第一个 _ 后的字符串，即章节名
            String title = StrUtil.subAfter(FileUtil.mainName(file), "_", false);
            Resource resource = new Resource(FileUtil.readBytes(file), ++i + ".html");
            book.addSection(title, resource);
        }

        EpubWriter epubWriter = new EpubWriter();

        String savePath = StrUtil.format("{}/{}.epub", dir.getParent(), novelInfo.getBookName());
        epubWriter.write(book, new FileOutputStream(savePath));
    }

    private void mergeTxt(File dir, String... args) {
        String path = StrUtil.format("{}{}{} ({}).txt",
                System.getProperty("user.dir") + File.separator, SAVE_PATH + File.separator, args[0], args[1]);
        File file = FileUtil.touch(path);
        FileAppender appender = new FileAppender(file, 16, true);

        for (File item : files(dir)) {
            String s = FileUtil.readString(item, StandardCharsets.UTF_8);
            appender.append(s);
        }
        appender.flush();
    }

    // 文件排序，按文件名升序
    private List<File> files(File dir) {
        return Arrays.stream(dir.listFiles())
                .sorted((o1, o2) -> {
                    String s1 = o1.getName();
                    String s2 = o2.getName();
                    int no1 = Integer.parseInt(s1.substring(0, s1.indexOf("_")));
                    int no2 = Integer.parseInt(s2.substring(0, s2.indexOf("_")));
                    return no1 - no2;
                }).toList();
    }

}