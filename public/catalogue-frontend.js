$(function() { //document ready event

  // Controls the expand/collapse of the what's new and blog post sections on the front page
  $('.recent-box-expand-link').on('click',function(event) {
    event.preventDefault();
    var growDiv     = $(this).siblings('.recent-box-grow').first();
    var wrapper     = growDiv.find(".recent-box-collapse");
    var smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      $(this).text('See all...');
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      $(this).text('...Collapse');
    }
  });

  // Controls the expand/collapse of the what's new and blog post sections on the front page
  $('.board-expand-link').on('click',function(event) {
    event.preventDefault();
    var growDiv     = $(this).parent().siblings('.board-grow').first();
    var wrapper     = growDiv.find(".board-collapse");
    var smallHeight = 120; //Should match css value

    if (growDiv.height() > smallHeight) {
      growDiv.css("height",smallHeight + "px"); //Collapse to small height
      $(this).text('See all');
    } else {
      growDiv.css("height",wrapper.innerHeight() + "px"); //Expand to height of inner content
      $(this).text('Collapse');
    }
  });

  const externalLinkAudit = (target) => {
    const body = {
      "target"  : target
    };

    const auditData = new Blob([JSON.stringify(body)]);
    navigator.sendBeacon("/audit", auditData);
    return true;
  }

  const addExternalLinkAuditEvent = function() {
    document.querySelectorAll("a.external-link-audit").forEach(function(current) {
      current.addEventListener("click", function(e) { return externalLinkAudit(this.href); });
    });
  };

  addExternalLinkAuditEvent();
});
