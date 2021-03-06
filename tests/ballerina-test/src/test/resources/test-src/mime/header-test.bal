import ballerina.mime;

function testAddHeader (string headerName, string headerValue, string headerNameToBeUsedForRetrieval) (string) {
    mime:Entity entity = {};
    entity.addHeader(headerName, headerValue);
    return entity.getHeader(headerNameToBeUsedForRetrieval);
}

function testAddingMultipleHeaders () (string, string, string) {
    mime:Entity entity = {};
    entity.addHeader("header1", "value1");
    entity.addHeader("header2", "value2");
    entity.addHeader("header3", "value3");
    return entity.getHeader("header1"), entity.getHeader("header2"), entity.getHeader("header3");
}

function testAddingMultipleValuesToSameHeader () (string[], string) {
    mime:Entity entity = {};
    entity.addHeader("heAder1", "value1");
    entity.addHeader("header1", "value2");
    entity.addHeader("header1", "value3");
    entity.addHeader("hEader2", "value3");
    entity.addHeader("headeR2", "value4");
    return entity.getHeaders("header1"), entity.getHeader("header2");
}

function testSetHeader () (string[], string) {
    mime:Entity entity = {};
    entity.setHeader("HeADEr2", "totally different value");
    return entity.getHeaders("header1"), entity.getHeader("header2");
}

function testSetHeaderAfterAddHeader () (string[], string) {
    mime:Entity entity = {};
    entity.addHeader("heAder1", "value1");
    entity.addHeader("header1", "value2");
    entity.addHeader("header1", "value3");
    entity.addHeader("hEader2", "value3");
    entity.addHeader("headeR2", "value4");
    entity.setHeader("HeADEr2", "totally different value");
    return entity.getHeaders("header1"), entity.getHeader("header2");
}


function testAddHeaderAfterSetHeader () (string[], string) {
    mime:Entity entity = {};
    entity.addHeader("heAder1", "value1");
    entity.addHeader("header1", "value2");
    entity.addHeader("header1", "value3");
    entity.addHeader("hEader2", "value3");
    entity.setHeader("HeADEr2", "totally different value");
    entity.addHeader("headeR2", "value4");
    return entity.getHeaders("header2"), entity.getHeader("header2");
}

function testRemoveHeader () (string[], string) {
    mime:Entity entity = {};
    entity.addHeader("heAder1", "value1");
    entity.addHeader("header1", "value2");
    entity.addHeader("header1", "value3");
    entity.addHeader("hEader2", "value3");
    entity.addHeader("headeR2", "value4");
    entity.setHeader("HeADEr2", "totally different value");
    entity.removeHeader("HEADER1");
    entity.removeHeader("NONE_EXISTENCE_HEADER");
    return entity.getHeaders("header1"), entity.getHeader("header2");
}

function testNonExistenceHeader () (string) {
    mime:Entity entity = {};
    entity.addHeader("heAder1", "value1");
    return entity.getHeader("header");
}
