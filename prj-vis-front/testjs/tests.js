
test( "hello test", function() {
  ok( 1 == "1", "Passed!" );
});


test( "ajax support", function() {
  ok( liftAjax.n == 3, "wrong number" );
});



