package br.com.critplay.diary;

import jakarta.validation.constraints.*;

public record DiaryRequest(@NotNull PlayStatus status,
                           @Min(1) @Max(5) Integer rating,
                           @Size(max = 2000) String review,
                           boolean favorite, Boolean wishlist,
                           @PastOrPresent java.time.LocalDate startDate,@PastOrPresent java.time.LocalDate finishDate) {
    public DiaryRequest(PlayStatus status,Integer rating,String review,boolean favorite,Boolean wishlist){this(status,rating,review,favorite,wishlist,null,null);}
    @AssertTrue public boolean isDateOrderValid(){return finishDate==null||startDate!=null&&!finishDate.isBefore(startDate);}
}
