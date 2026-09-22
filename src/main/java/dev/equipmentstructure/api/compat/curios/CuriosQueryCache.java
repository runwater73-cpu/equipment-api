package dev.equipmentstructure.api.compat.curios;

/** Internal bridge to invalidate native per-tick query caches after an equipment ownership change. */
public interface CuriosQueryCache { void equipment$invalidateQueries(); }
