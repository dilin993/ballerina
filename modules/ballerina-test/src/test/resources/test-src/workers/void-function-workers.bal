import ballerina.lang.system;

int i = 0;

function testVoidFunction()(int){
    testVoid();
    return i;
}

function testVoid() {
    worker w1 {
        system:sleep(3000);
        testNew();
    }
    worker w2 {
         int x = i + 10;
    }
}

function testNew(){
    worker w1 {
        system:sleep(2000);
    }
    worker w2 {
        i = 10;
    }
}

