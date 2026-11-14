package ars.home.fileserver.dto;

import java.util.List;

/**
 * Ответ со списком книг и интервалом автообновления страницы.
 */
public record BookListDto(List<BookFileDto> files, long refreshIntervalSeconds) {
}
