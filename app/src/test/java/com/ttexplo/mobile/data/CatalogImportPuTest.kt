package com.ttexplo.mobile.data

import com.google.common.truth.Truth.assertThat
import com.ttexplo.mobile.data.local.dao.ProductoDao
import com.ttexplo.mobile.data.local.entity.ProductoEntity
import com.ttexplo.mobile.data.repository.CatalogRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CatalogImportPuTest {

    private class FakeDao : ProductoDao {
        var inserted: List<ProductoEntity> = emptyList()
        var existing: List<ProductoEntity> = emptyList()
        override suspend fun getById(id: String): ProductoEntity? = null
        override suspend fun getByCodBarras(codBarras: String): ProductoEntity? = null
        override fun getAllFlow(): Flow<List<ProductoEntity>> = emptyFlow()
        override suspend fun getAll(): List<ProductoEntity> = existing
        override suspend fun count(): Int = inserted.size
        override suspend fun insert(producto: ProductoEntity) { inserted = inserted + producto }
        override suspend fun delete(id: String) {}
        override suspend fun search(query: String): List<ProductoEntity> = emptyList()
        override suspend fun insertAll(productos: List<ProductoEntity>) { inserted = productos }
        override suspend fun updateUnitWeight(id: String, unitWeightKg: Double, cartridgesPerBox: Int) {}
        override suspend fun deleteAll() { inserted = emptyList() }
    }

    @Test
    fun mapsPuColumnToUnitWeight() = runBlocking {
        val dao = FakeDao()
        val csv = """
            "IdProducto","Producto","Presentacion","noSAP","Unidad","Cantidad","CodBarras","peso","PG","PU"
            "310","EMULEX 1","2.54 CM X 20.32 CM (1"" X 8"")","111921","KG",25,"310",25,"0035",0.119047619047619
        """.trimIndent()

        val count = CatalogRepositoryImpl(dao).importCsv(csv)

        assertThat(count).isEqualTo(1)
        val product = dao.inserted.first { it.idProducto == "310" }
        assertThat(product.pesoUnitarioKg).isWithin(1e-9).of(0.119047619047619)
    }

    @Test
    fun preservesExistingCalibrationWhenPuIsPresent() = runBlocking {
        val dao = FakeDao()
        dao.existing = listOf(
            ProductoEntity(
                idProducto = "310",
                producto = "EMULEX 1",
                presentacion = "1 X 8",
                noSap = "111921",
                unidad = "KG",
                cantidad = 25.0,
                codBarras = "310",
                peso = 25.0,
                pg = "0035",
                pu = 0.119047619047619,
                tipo = "ALTO_EXPLOSIVO",
                pesoUnitarioKg = 0.5,
                cartuchosPorCaja = 50
            )
        )
        val csv = """
            "IdProducto","Producto","Presentacion","noSAP","Unidad","Cantidad","CodBarras","peso","PG","PU"
            "310","EMULEX 1","1 X 8","111921","KG",25,"310",25,"0035",0.119047619047619
        """.trimIndent()

        CatalogRepositoryImpl(dao).importCsv(csv)

        assertThat(dao.inserted.first().pesoUnitarioKg).isWithin(1e-9).of(0.5)
    }
}
