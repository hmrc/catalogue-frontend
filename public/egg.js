$(function() { //document ready event

  $('#egg').on('click',function(e) {
    e.preventDefault();
    randomiseNames();
    //activate
    $("#platops").css("display", "flex");
    $("body").css("background-color", "#141114");
    $("body").css("background-image", "linear-gradient(335deg, black 23px, transparent 23px),linear-gradient(155deg, black 23px, transparent 23px),linear-gradient(335deg, black 23px, transparent 23px),linear-gradient(155deg, black 23px, transparent 23px)");
    $("body").css("background-size", "58px 58px");
    $("body").css("background-position", "0px 2px, 4px 35px, 29px 31px, 34px 6px");

    $('#platops').one('click', function(e){
        e.preventDefault();
        //deactivate
        $("#platops").css("display", "none");
        $("body").css("background-color", "");
        $("body").css("background-image", "");
        $("body").css("background-size", "");
        $("body").css("background-position", "");
    });

   });

    function randomiseNames() {
        $(".randomplace").each(function () {

        var randomtop = Math.floor(Math.random() * ($(document).height() - $(this).height() - 200)),
            randomleft = Math.floor(Math.random() * ($(document).width() - $(this).width() - 200)),
            randomzindex = Math.floor(Math.random() * 30);
        $(this).css({
                "top": randomtop,
                "left": randomleft,
                "z-index": randomzindex
            });
        });
    }

});