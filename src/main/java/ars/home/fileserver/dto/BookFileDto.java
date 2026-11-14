package ars.home.fileserver.dto;

/**
 * Описание файла книги, отображаемого в таблице на главной странице.
 *
 * @param name     имя файла в каталоге (используется для скачивания)
 * @param author   фамилия и инициалы автора
 * @param title    название книги
 * @param size     размер файла в байтах
 * @param modified время последнего изменения в миллисекундах
 */
public record BookFileDto(String name, String author, String title, long size, long modified) {
}
