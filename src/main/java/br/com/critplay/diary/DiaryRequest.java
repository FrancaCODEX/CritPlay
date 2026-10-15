package br.com.critplay.diary;

import jakarta.validation.constraints.*;

public record DiaryRequest(@NotNull PlayStatus status,
                           @DecimalMin("0.5") @DecimalMax("5") Double rating,
                           @Size(max = 2000) String review,
                           boolean favorite, Boolean wishlist,
                           @PastOrPresent java.time.LocalDate startDate,@PastOrPresent java.time.LocalDate finishDate) {
    public DiaryRequest(PlayStatus status,Number rating,String review,boolean favorite,Boolean wishlist){this(status,rating==null?null:rating.doubleValue(),review,favorite,wishlist,null,null);}
    @AssertTrue public boolean isHalfStarRating(){return rating==null||Double.isFinite(rating)&&rating*2==Math.floor(rating*2);}
    @AssertTrue public boolean isDateOrderValid(){return finishDate==null||startDate!=null&&!finishDate.isBefore(startDate);}
}
