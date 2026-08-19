package org.uengine.five.analytics.etl.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "BPM_DIM_DATE")
public class AnalyticsDateDimension {

    @Id
    private Integer dateKey;
    private LocalDate calendarDate;
    private Integer yearNumber;
    private Integer quarterNumber;
    private Integer monthNumber;
    private Integer dayNumber;
    private Integer weekOfYear;
    private Integer dayOfWeek;
    private Boolean weekend;

    protected AnalyticsDateDimension() {
    }

    public AnalyticsDateDimension(Integer dateKey, LocalDate calendarDate, Integer yearNumber,
                                  Integer quarterNumber, Integer monthNumber, Integer dayNumber,
                                  Integer weekOfYear, Integer dayOfWeek, Boolean weekend) {
        this.dateKey = dateKey;
        this.calendarDate = calendarDate;
        this.yearNumber = yearNumber;
        this.quarterNumber = quarterNumber;
        this.monthNumber = monthNumber;
        this.dayNumber = dayNumber;
        this.weekOfYear = weekOfYear;
        this.dayOfWeek = dayOfWeek;
        this.weekend = weekend;
    }

    public Integer getDateKey() { return dateKey; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public Integer getYearNumber() { return yearNumber; }
    public Integer getQuarterNumber() { return quarterNumber; }
    public Integer getMonthNumber() { return monthNumber; }
    public Integer getDayNumber() { return dayNumber; }
    public Integer getWeekOfYear() { return weekOfYear; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public Boolean getWeekend() { return weekend; }
}
