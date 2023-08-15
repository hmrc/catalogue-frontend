$(function() { //document ready event

  // Controls the expand/collapse of the blog post section on the front page and the boards on repository pages
  $('.content-grow-expand-link').on('click',function(event) {
    event.preventDefault();
    let self = $(this);
    let growDiv     = self.parent().siblings('.content-grow').first();
    let wrapper     = growDiv.find(".content-grow-wrapper");
    let smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      growDiv.addClass("content-grow-gradient"); //Add gradient mask
      self.removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down'); //Set expand icon
      self.attr('title', 'See all');
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      growDiv.removeClass("content-grow-gradient"); //Remove gradient mask
      self.attr("title", "Collapse");
      self.removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up'); //Set collapse icon
    }
  });
});
