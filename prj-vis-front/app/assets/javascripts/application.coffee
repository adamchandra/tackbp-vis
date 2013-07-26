sortTable = (tableid, col) ->
  tbl = document.getElementById(tableid).tBodies[0]
  store = []
  for row in tbl.rows
    sortnr = parseFloat(row.cells[col].textContent || row.cells[col].innerText)
    if not isNaN(sortnr)
      store.push([sortnr, row]);

  store.sort((x,y) -> x[0] - y[0])

  for i in [0 ... store.length]
    $(tbl).append(store[i][1])

  store = null;
