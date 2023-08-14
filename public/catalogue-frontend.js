$(function() { //document ready event

  // Controls the expand/collapse of the blog post section on the front page
  $('.recent-box-expand-link').on('click',function(event) {
    event.preventDefault();
    let self = $(this);
    let growDiv     = self.parent().siblings('.recent-box-grow').first();
    let wrapper     = growDiv.find(".recent-box-collapse");
    let smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      growDiv.addClass("board-gradient"); //Add gradient mask
      self.removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down'); //Set expand icon
      self.attr('title', 'See all');
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      growDiv.removeClass("board-gradient"); //Remove gradient mask
      self.attr("title", "Collapse");
      self.removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up'); //Set collapse icon
    }
  });

  // Controls the expand/collapse of the boards on repository pages.
  $('.board-expand-link').on('click',function(event) {
    event.preventDefault();
    let self = $(this);
    let growDiv     = self.parent().siblings('.board-grow').first();
    let wrapper     = growDiv.find(".board-collapse");
    let smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      growDiv.addClass("board-gradient"); //Add gradient mask
      self.attr("title", "See all");
      self.removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down'); //Set expand icon
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      growDiv.removeClass("board-gradient"); //Remove gradient mask
      self.attr("title", "Collapse");
      self.removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up'); //Set collapse icon
    }
  });
});
