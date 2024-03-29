function hello() {
    return 'hello there 02'
}

function objectToString(obj) { 
    return _objectToString(obj, 0) 
}

function _objectToString(obj, level) { 
    var ostr = '';
    for (var fname in obj) { 
        var fval = obj[fname];
        var fvaltype = typeof(fval); 
        if (fvaltype==='object') { 
            if (typeof(fval.getTime) === 'function') {
                /* It's a date field */
                ostr = appendIndent(ostr, level, fname + ': ' + fval.toString() + '\n');
            } else if (fval.hasOwnProperty('len')) {
                /* It's a UUID */
                ostr = appendIndent(ostr, level, fname + ': ' + binToJUUID(fval) + ' = ' + fval.toString()  + '\n');
            } else {
                ostr = appendIndent(ostr, level, fname +':\n'+ _objectToString(fval, level+1));
            }
        } else if (fvaltype !== 'function') { 
            ostr = appendIndent(ostr, level, fname + ": " + fval.toString().trim() + '\n');
        } 
    } 
    return ostr;
}


function appendIndent(acc, level, str) {
    String.prototype.repeat = function( num ) { return new Array( num + 1 ).join( this ); }
    return acc + (' '.repeat(level*4) + str); 
}


function hasUUID(obj, uuidStr) {
    return objectHasUUID(obj, '{'+uuidStr+'}')
} 

function objectHasUUID(obj, juuidStr) { 
    for (var fname in obj) { 
        var fval = obj[fname];
        var fvaltype = typeof(fval); 
        if (fvaltype==='object') {
            if (fval.hasOwnProperty('len')) { 
                /* Its a UUID */
                var fvuuid = binToJUUID(fval);
                if (fvuuid === juuidStr) { 
                    return true; 
                }
            } else {
                if (objectHasUUID(fval, juuidStr)) {
                    return true;
                }
            }
        }
    } 
    return false;
}

function HexToBase64(hex) {
    var base64Digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    var base64 = "";
    var group;
    for (var i = 0; i < 30; i += 6) {
        group = parseInt(hex.substr(i, 6), 16);
        base64 += base64Digits[(group >> 18) & 0x3f];
        base64 += base64Digits[(group >> 12) & 0x3f];
        base64 += base64Digits[(group >> 6) & 0x3f];
        base64 += base64Digits[group & 0x3f];
    }
    group = parseInt(hex.substr(30, 2), 16);
    base64 += base64Digits[(group >> 2) & 0x3f];
    base64 += base64Digits[(group << 4) & 0x3f];
    base64 += "==";
    return base64;
}

function Base64ToHex(base64) {
    var base64Digits = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    var hexDigits = "0123456789abcdef";
    var hex = "";
    for (var i = 0; i < 24; ) {
        var e1 = base64Digits.indexOf(base64[i++]);
        var e2 = base64Digits.indexOf(base64[i++]);
        var e3 = base64Digits.indexOf(base64[i++]);
        var e4 = base64Digits.indexOf(base64[i++]);
        var c1 = (e1 << 2) | (e2 >> 4);
        var c2 = ((e2 & 15) << 4) | (e3 >> 2);
        var c3 = ((e3 & 3) << 6) | e4;
        hex += hexDigits[c1 >> 4];
        hex += hexDigits[c1 & 15];
        if (e3 != 64) {
            hex += hexDigits[c2 >> 4];
            hex += hexDigits[c2 & 15];
        }
        if (e4 != 64) {
            hex += hexDigits[c3 >> 4];
            hex += hexDigits[c3 & 15];
        }
    }
    return hex;
}

function juuidToBinData(uuid) {
    var hex = uuid.replace(/[{}-]/g, ""); // remove extra characters
    var msb = hex.substr(0, 16);
    var lsb = hex.substr(16, 16);
    msb = msb.substr(14, 2) + msb.substr(12, 2) + msb.substr(10, 2) + msb.substr(8, 2) + msb.substr(6, 2) + msb.substr(4, 2) + msb.substr(2, 2) + msb.substr(0, 2);
    lsb = lsb.substr(14, 2) + lsb.substr(12, 2) + lsb.substr(10, 2) + lsb.substr(8, 2) + lsb.substr(6, 2) + lsb.substr(4, 2) + lsb.substr(2, 2) + lsb.substr(0, 2);
    hex = msb + lsb;
    var base64 = HexToBase64(hex);
    return new BinData(3, base64);
}


function binToJUUID(bdata) {
    var hex = Base64ToHex(bdata.base64()); // don't use BinData's hex function because it has bugs in older versions of the shell
    var msb = hex.substr(0, 16);
    var lsb = hex.substr(16, 16);
    msb = msb.substr(14, 2) + msb.substr(12, 2) + msb.substr(10, 2) + msb.substr(8, 2) + msb.substr(6, 2) + msb.substr(4, 2) + msb.substr(2, 2) + msb.substr(0, 2);
    lsb = lsb.substr(14, 2) + lsb.substr(12, 2) + lsb.substr(10, 2) + lsb.substr(8, 2) + lsb.substr(6, 2) + lsb.substr(4, 2) + lsb.substr(2, 2) + lsb.substr(0, 2);
    hex = msb + lsb;
    var uuid = hex.substr(0, 8) + '-' + hex.substr(8, 4) + '-' + hex.substr(12, 4) + '-' + hex.substr(16, 4) + '-' + hex.substr(20, 12);
    return '{' + uuid + '}';
}


function findByJuuid(juuid) {
    var cnames = colls();
    var rval = [];
    for (var i = 0; i < cnames.length; i+=1) { 
        var cn = cnames[i];
        var col = db.getCollection(cn);
        print("searching (name) "+cn);
        var found = col.findOne({_id: juuidToBinData(juuid)});
        if (found !== null) {
            print("found: "+found);
            rval.push(found);
        }
    }
    return rval;
}

function colls() {
    return [
        "document",
        "event",
        "eventprocessor",
        "eventprocessor_archive",
        "linkedaccount",
        "token",
        "user",
        "user_archive",
        "userpassword"
    ]
}


function installJs() {
    var fns = [
        { _id: "colls",           value: colls },
        { _id: "findByJuuid",     value: findByJuuid },
        { _id: "hello",           value: hello },
        { _id: "_objectToString", value: _objectToString },
        { _id: "appendIndent",    value: appendIndent },
        { _id: "objectToString",  value: objectToString },
        { _id: "hasUUID",         value: hasUUID },
        { _id: "objectHasUUID",   value: objectHasUUID },
        { _id: "HexToBase64",     value: HexToBase64 },
        { _id: "Base64ToHex",     value: Base64ToHex }, 
        { _id: "juuidToBinData",  value: juuidToBinData },
        { _id: "binToJUUID",      value: binToJUUID }
    ]

    db.system.js.remove({})

    for (var i = 0; i < fns.length; i++) {
        print('saving '+fns[i]._id)
        db.system.js.save(fns[i])
    }
}


