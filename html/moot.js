function newMap() {
    var mapOptions = {
        center: { lat: -34.397, lng: 150.644 },
        zoom: 8
    };
    var div = document.createElement('div');
    div.id = 'map';
    document.body.appendChild(div);
    console.log("newMap()");
    var map = new google.maps.Map(div, mapOptions);
}

google.maps.event.addDomListener(window, 'load', newMap);
