package samrock.converters.cleanupupdate;

import sam.manga.newsamrock.chapters.Chapter;

class ListManga {
    final int chap_count_pc;
    final String manga_name;
    final long last_update_time;
    final Chapter lastChapter;

    ListManga(int chap_count_pc, String manga_name, long last_update_time, Chapter lastChapter) {
        this.chap_count_pc = chap_count_pc;
        this.manga_name = manga_name;
        this.last_update_time = last_update_time;
        this.lastChapter = lastChapter;
    }
}