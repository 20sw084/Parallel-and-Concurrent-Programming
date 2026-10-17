public class Main
{
    static void printMatrix(int M[][])
    {
        for (int i = 0; i <  M.length; i++) {
            for (int j = 0; j <  M[i].length; j++)
                System.out.print(M[i][j] + " ");

            System.out.println();
        }
    }
    
    public static void multiplyMatrix(int[][] a, int[][] b){
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return;
        }
    
        if (a[0].length != b.length) {
            throw new IllegalArgumentException(
                "Columns of A must equal rows of B"
            );
        }

        int[][] res = new int[a.length][b[0].length];
        for (int i = 0; i<a.length;i++){
            for (int j = 0; j<b[0].length;j++){
                for (int k = 0; k<b.length;k++){
                    res[i][j]+=a[i][k]*b[k][j];
                }
            }
        }
        printMatrix(res);
    }
    
	public static void main(String[] args)
    {
        int A[][] = { { 1, 1, 1 },
                      { 2, 2, 2 },
                      { 3, 3, 3 },
                      { 4, 4, 4 } };

        int B[][] = { { 1, 1, 1, 1 },
                      { 2, 2, 2, 2 },
                      { 3, 3, 3, 3 } };

        multiplyMatrix(A, B);
    }
}
