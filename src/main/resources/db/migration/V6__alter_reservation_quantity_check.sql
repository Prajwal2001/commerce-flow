alter table reservation
    drop constraint positive_quantity,
    add constraint positive_quantity check ( quantity > 0 );